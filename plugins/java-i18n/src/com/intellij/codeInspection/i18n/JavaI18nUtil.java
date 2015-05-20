/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.template.macro.MacroUtil;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.*;

/**
 * @author max
 */
public class JavaI18nUtil extends I18nUtil {
  public static final PropertyCreationHandler DEFAULT_PROPERTY_CREATION_HANDLER = new PropertyCreationHandler() {
    @Override
    public void createProperty(final Project project, final Collection<PropertiesFile> propertiesFiles, final String key, final String value,
                               final PsiExpression[] parameters) throws IncorrectOperationException {
      JavaI18nUtil.createProperty(project, propertiesFiles, key, value);
    }
  };

  private JavaI18nUtil() {
  }

  @Nullable
  public static TextRange getSelectedRange(Editor editor, final PsiFile psiFile) {
    if (editor == null) return null;
    String selectedText = editor.getSelectionModel().getSelectedText();
    if (selectedText != null) {
      return new TextRange(editor.getSelectionModel().getSelectionStart(), editor.getSelectionModel().getSelectionEnd());
    }
    PsiElement psiElement = psiFile.findElementAt(editor.getCaretModel().getOffset());
    if (psiElement==null || psiElement instanceof PsiWhiteSpace) return null;
    return psiElement.getTextRange();
  }

  public static boolean mustBePropertyKey(@NotNull Project project,
                                          @NotNull PsiExpression expression,
                                          @NotNull Map<String, Object> annotationAttributeValues) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiVariable)parent, AnnotationUtil.PROPERTY_KEY);
      if (annotation != null) {
        return processAnnotationAttributes(annotationAttributeValues, annotation);
      }
    }
    return isPassedToAnnotatedParam(project, expression, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null);
  }

  public static boolean isPassedToAnnotatedParam(@NotNull Project project,
                                                 @NotNull PsiExpression expression,
                                                 final String annFqn,
                                                 @Nullable Map<String, Object> annotationAttributeValues,
                                                 @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = getToplevelExpression(project, expression);
    final PsiElement parent = expression.getParent();

    if (!(parent instanceof PsiExpressionList)) return false;
    int idx = -1;
    final PsiExpression[] args = ((PsiExpressionList)parent).getExpressions();
    for (int i = 0; i < args.length; i++) {
      PsiExpression arg = args[i];
      if (PsiTreeUtil.isAncestor(arg, expression, false)) {
        idx = i;
        break;
      }
    }
    if (idx == -1) return false;

    PsiElement grParent = parent.getParent();

    if (grParent instanceof PsiAnonymousClass) {
      grParent = grParent.getParent();
    }

    if (grParent instanceof PsiCall) {
      PsiMethod method = ((PsiCall)grParent).resolveMethod();
      if (method != null && isMethodParameterAnnotatedWith(method, idx, null, annFqn, annotationAttributeValues, nonNlsTargets)) {
        return true;
      }
    }

    return false;
  }

  private static final Key<ParameterizedCachedValue<PsiExpression, Pair<Project, PsiExpression>>> TOP_LEVEL_EXPRESSION = Key.create("TOP_LEVEL_EXPRESSION");
  private static final ParameterizedCachedValueProvider<PsiExpression, Pair<Project, PsiExpression>> TOP_LEVEL_PROVIDER =
    new ParameterizedCachedValueProvider<PsiExpression, Pair<Project, PsiExpression>>() {
      @Override
      public CachedValueProvider.Result<PsiExpression> compute(Pair<Project, PsiExpression> pair) {
        PsiExpression param = pair.second;
        Project project = pair.first;
        PsiExpression topLevel = getTopLevel(project, param);
        ParameterizedCachedValue<PsiExpression, Pair<Project, PsiExpression>> cachedValue = param.getUserData(TOP_LEVEL_EXPRESSION);
        assert cachedValue != null;
        int i = 0;
        for (PsiElement element = param; element != topLevel; element = element.getParent(), i++) {
          if (i % 10 == 0) {   // optimization: store up link to the top level expression in each 10nth element
            element.putUserData(TOP_LEVEL_EXPRESSION, cachedValue);
          }
        }
        return CachedValueProvider.Result.create(topLevel, PsiManager.getInstance(project).getModificationTracker());
      }
    };

  @NotNull
  public static PsiExpression getToplevelExpression(@NotNull final Project project, @NotNull final PsiExpression expression) {
    if (expression instanceof PsiBinaryExpression || expression.getParent() instanceof PsiBinaryExpression) {  //can be large, cache
      return CachedValuesManager.getManager(project).getParameterizedCachedValue(expression, TOP_LEVEL_EXPRESSION, TOP_LEVEL_PROVIDER, true,
                                                                                 Pair.create(project, expression));
    }
    return getTopLevel(project, expression);
  }

  @NotNull
  private static PsiExpression getTopLevel(Project project, @NotNull PsiExpression expression) {
    int i = 0;
    while (expression.getParent() instanceof PsiExpression) {
      i++;
      final PsiExpression parent = (PsiExpression)expression.getParent();
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getCondition() == expression) break;
      expression = parent;
      if (expression instanceof PsiAssignmentExpression) break;
      if (i > 10 && expression instanceof PsiBinaryExpression) {
        ParameterizedCachedValue<PsiExpression, Pair<Project, PsiExpression>> value = expression.getUserData(TOP_LEVEL_EXPRESSION);
        if (value != null && value.hasUpToDateValue()) {
          return getToplevelExpression(project, expression); // optimization: use caching for big hierarchies
        }
      }
    }
    return expression;
  }

  public static boolean isMethodParameterAnnotatedWith(final PsiMethod method,
                                                       final int idx,
                                                       @Nullable Collection<PsiMethod> processed,
                                                       final String annFqn,
                                                       @Nullable Map<String, Object> annotationAttributeValues,
                                                       @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    if (processed != null) {
      if (processed.contains(method)) return false;
    }
    else {
      processed = new THashSet<PsiMethod>();
    }
    processed.add(method);

    final PsiParameter[] params = method.getParameterList().getParameters();
    PsiParameter param;
    if (idx >= params.length) {
      if (params.length == 0) {
        return false;
      }
      PsiParameter lastParam = params [params.length-1];
      if (lastParam.isVarArgs()) {
        param = lastParam;
      }
      else {
        return false;
      }
    }
    else {
      param = params[idx];
    }
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(param, annFqn);
    if (annotation != null) {
      return processAnnotationAttributes(annotationAttributeValues, annotation);
    }
    if (nonNlsTargets != null) {
      nonNlsTargets.add(param);
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (isMethodParameterAnnotatedWith(superMethod, idx, processed, annFqn, annotationAttributeValues, null)) return true;
    }

    return false;
  }

  private static boolean processAnnotationAttributes(@Nullable Map<String, Object> annotationAttributeValues, @NotNull PsiAnnotation annotation) {
    if (annotationAttributeValues != null) {
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        final String name = attribute.getName();
        if (annotationAttributeValues.containsKey(name)) {
          annotationAttributeValues.put(name, attribute.getValue());
        }
      }
    }
    return true;
  }

  public static boolean isValidPropertyReference(@NotNull Project project,
                                                 @NotNull PsiExpression expression,
                                                 @NotNull String key,
                                                 @NotNull Ref<String> outResourceBundle) {
    final HashMap<String, Object> annotationAttributeValues = new HashMap<String, Object>();
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    if (mustBePropertyKey(project, expression, annotationAttributeValues)) {
      final Object resourceBundleName = annotationAttributeValues.get(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
      if (!(resourceBundleName instanceof PsiExpression)) {
        return false;
      }
      PsiExpression expr = (PsiExpression)resourceBundleName;
      final PsiConstantEvaluationHelper constantEvaluationHelper = JavaPsiFacade.getInstance(project).getConstantEvaluationHelper();
      Object value = constantEvaluationHelper.computeConstantExpression(expr);
      if (value == null) {
        if (expr instanceof PsiReferenceExpression) {
          final PsiElement resolve = ((PsiReferenceExpression)expr).resolve();
          if (resolve instanceof PsiField && ((PsiField)resolve).hasModifierProperty(PsiModifier.FINAL)) {
            value = constantEvaluationHelper.computeConstantExpression(((PsiField)resolve).getInitializer());
            if (value == null) {
              return false;
            }
          }
        }
        if (value == null) {
          final ResourceBundle resourceBundle = resolveResourceBundleByKey(key, project);
          if (resourceBundle == null) {
            return false;
          }
          final PropertiesFile defaultPropertiesFile = resourceBundle.getDefaultPropertiesFile();
          final String bundleName = BundleNameEvaluator.DEFAULT.evaluateBundleName(defaultPropertiesFile.getContainingFile());
          if (bundleName == null) {
            return false;
          }
          value = bundleName;
        }
      }
      String bundleName = value.toString();
      outResourceBundle.set(bundleName);
      return isPropertyRef(expression, key, bundleName);
    }
    return true;
  }

  @Nullable
  public static ResourceBundle resolveResourceBundleByKey(final @NotNull String key, final @NotNull Project project) {
    final Ref<ResourceBundle> bundleRef = Ref.create();
    final boolean r = PropertiesReferenceManager.getInstance(project).processAllPropertiesFiles(new PropertiesFileProcessor() {
      @Override
      public boolean process(String baseName, PropertiesFile propertiesFile) {
        if (propertiesFile.findPropertyByKey(key) != null) {
          if (bundleRef.get() == null) {
            bundleRef.set(propertiesFile.getResourceBundle());
          }
          else {
            if (!bundleRef.get().equals(propertiesFile.getResourceBundle())) {
              return false;
            }
          }
        }
        return true;
      }
    });
    return r ? bundleRef.get() : null;
  }

  public static boolean isPropertyRef(final PsiExpression expression, final String key, final String resourceBundleName) {
    if (resourceBundleName == null) {
      return !PropertiesImplUtil.findPropertiesByKey(expression.getProject(), key).isEmpty();
    }
    else {
      final List<PropertiesFile> propertiesFiles = propertiesFilesByBundleName(resourceBundleName, expression);
      boolean containedInPropertiesFile = false;
      for (PropertiesFile propertiesFile : propertiesFiles) {
        containedInPropertiesFile |= propertiesFile.findPropertyByKey(key) != null;
      }
      return containedInPropertiesFile;
    }
  }

  public static Set<String> suggestExpressionOfType(final PsiClassType type, final PsiLiteralExpression context) {
    PsiVariable[] variables = MacroUtil.getVariablesVisibleAt(context, "");
    Set<String> result = new LinkedHashSet<String>();
    for (PsiVariable var : variables) {
      PsiType varType = var.getType();
      if (type == null || type.isAssignableFrom(varType)) {
        result.add(var.getNameIdentifier().getText());
      }
    }

    PsiExpression[] expressions = MacroUtil.getStandardExpressionsOfType(context, type);
    for (PsiExpression expression : expressions) {
      result.add(expression.getText());
    }
    if (type != null) {
      addAvailableMethodsOfType(type, context, result);
    }
    return result;
  }

  private static void addAvailableMethodsOfType(final PsiClassType type, final PsiLiteralExpression context, final Collection<String> result) {
    PsiScopesUtil.treeWalkUp(new PsiScopeProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (element instanceof PsiMethod) {
          PsiMethod method = (PsiMethod)element;
          PsiType returnType = method.getReturnType();
          if (returnType != null && TypeConversionUtil.isAssignable(type, returnType)
              && method.getParameterList().getParametersCount() == 0) {
            result.add(method.getName() + "()");
          }
        }
        return true;
      }

      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return null;
      }

      @Override
      public void handleEvent(@NotNull Event event, Object associated) {

      }
    }, context, null);
  }

  /**
   * Returns number of different message format parameters in property value
   *
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i>
   * number of parameters is 3.
   *
   * @param propertyValue
   * @return number of parameters from single property or 0 for wrong format
   */
  public static int getPropertyValuePlaceholdersCount(final @NotNull String propertyValue) {
    try {
      return new MessageFormat(propertyValue).getFormatsByArgumentIndex().length;
    } catch (final IllegalArgumentException e) {
      return 0;
    }
  }

  /**
   * Returns number of different parameters in i18n message. For example, for string
   *
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i> in one translation of property
   * <i>Class {0} info: Class {0} extends class {1} </i> in other translation of property
   *
   * number of parameters is 3.
   *
   * @param expression i18n literal
   * @return number of parameters
   */
  public static int getPropertyValueParamsMaxCount(final @NotNull PsiExpression expression) {
    return getPropertyValueParamsMaxCount(expression, null);
  }

  public static int getPropertyValueParamsMaxCount(final @NotNull PsiExpression expression, final @Nullable String resourceBundleName) {
    final SortedSet<Integer> paramsCount = getPropertyValueParamsCount(expression, resourceBundleName);
    if (paramsCount.isEmpty()) {
      return -1;
    }
    return paramsCount.last();
  }

  @NotNull
  public static SortedSet<Integer> getPropertyValueParamsCount(final @NotNull PsiExpression expression, final @Nullable String resourceBundleName) {
    final PsiLiteralExpression literalExpression;
    if (expression instanceof PsiLiteralExpression) {
      literalExpression = (PsiLiteralExpression)expression;
    } else if (expression instanceof PsiReferenceExpression) {
      final PsiElement resolved = ((PsiReferenceExpression)expression).resolve();
      final PsiField field = resolved == null ? null : (PsiField)resolved;
      literalExpression =
        field != null && field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() instanceof PsiLiteralExpression
        ? (PsiLiteralExpression)field.getInitializer()
        : null;
    } else {
      literalExpression = null;
    }
    final TreeSet<Integer> paramsCount = new TreeSet<Integer>();
    if (literalExpression == null) {
      return paramsCount;
    }
    for (PsiReference reference : literalExpression.getReferences()) {
      if (reference instanceof PsiPolyVariantReference) {
        for (ResolveResult result : ((PsiPolyVariantReference)reference).multiResolve(false)) {
          if (result.isValidResult() && result.getElement() instanceof IProperty) {
            try {
              final IProperty property = (IProperty)result.getElement();
              if (resourceBundleName != null) {
                final PsiFile file = property.getPropertiesFile().getContainingFile();
                if (!resourceBundleName.equals(BundleNameEvaluator.DEFAULT.evaluateBundleName(file))) {
                  continue;
                }
              }
              final String propertyValue = property.getValue();
              if (propertyValue == null) {
                continue;
              }
              paramsCount.add(getPropertyValuePlaceholdersCount(propertyValue));
            }
            catch (IllegalArgumentException ignored) {
            }
          }
        }
      }
    }
    return paramsCount;
  }
}
