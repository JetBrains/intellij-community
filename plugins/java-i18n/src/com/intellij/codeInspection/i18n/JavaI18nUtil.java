/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
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
      I18nUtil.createProperty(project, propertiesFiles, key, value, true);
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

  public static boolean mustBePropertyKey(@NotNull PsiExpression expression,
                                          @NotNull Map<String, Object> annotationAttributeValues) {
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiVariable) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiVariable)parent, AnnotationUtil.PROPERTY_KEY);
      if (annotation != null) {
        return processAnnotationAttributes(annotationAttributeValues, annotation);
      }
    }
    return isPassedToAnnotatedParam(expression, AnnotationUtil.PROPERTY_KEY, annotationAttributeValues, null);
  }

  static boolean isPassedToAnnotatedParam(@NotNull PsiExpression expression,
                                          final String annFqn,
                                          @Nullable Map<String, Object> annotationAttributeValues,
                                          @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    expression = getTopLevelExpression(expression);
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

  @NotNull
  static PsiExpression getTopLevelExpression(@NotNull PsiExpression expression) {
    while (expression.getParent() instanceof PsiExpression) {
      final PsiExpression parent = (PsiExpression)expression.getParent();
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getCondition() == expression) break;
      expression = parent;
      if (expression instanceof PsiAssignmentExpression) break;
    }
    return expression;
  }

  static boolean isMethodParameterAnnotatedWith(final PsiMethod method,
                                                final int idx,
                                                @Nullable Collection<PsiMethod> processed,
                                                final String annFqn,
                                                @Nullable Map<String, Object> annotationAttributeValues,
                                                @Nullable final Set<PsiModifierListOwner> nonNlsTargets) {
    if (processed != null) {
      if (processed.contains(method)) return false;
    }
    else {
      processed = new THashSet<>();
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

  static boolean isValidPropertyReference(@NotNull Project project,
                                          @NotNull PsiExpression expression,
                                          @NotNull String key,
                                          @NotNull Ref<String> outResourceBundle) {
    final HashMap<String, Object> annotationAttributeValues = new HashMap<>();
    annotationAttributeValues.put(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER, null);
    if (mustBePropertyKey(expression, annotationAttributeValues)) {
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
  private static ResourceBundle resolveResourceBundleByKey(@NotNull final String key, @NotNull final Project project) {
    final Ref<ResourceBundle> bundleRef = Ref.create();
    final boolean r = PropertiesReferenceManager.getInstance(project).processAllPropertiesFiles((baseName, propertiesFile) -> {
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
    });
    return r ? bundleRef.get() : null;
  }

  static boolean isPropertyRef(final PsiExpression expression, final String key, final String resourceBundleName) {
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
    Set<String> result = new LinkedHashSet<>();
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
    }, context, null);
  }

  /**
   * Returns number of different message format parameters in property value
   *
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i>
   * number of parameters is 3.
   *
   * @return number of parameters from single property or 0 for wrong format
   */
  public static int getPropertyValuePlaceholdersCount(@NotNull final String propertyValue) {
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
  public static int getPropertyValueParamsMaxCount(@NotNull final PsiExpression expression) {
    return getPropertyValueParamsMaxCount(expression, null);
  }

  private static int getPropertyValueParamsMaxCount(@NotNull final PsiExpression expression, @Nullable final String resourceBundleName) {
    final SortedSet<Integer> paramsCount = getPropertyValueParamsCount(expression, resourceBundleName);
    if (paramsCount.isEmpty()) {
      return -1;
    }
    return paramsCount.last();
  }

  @NotNull
  static SortedSet<Integer> getPropertyValueParamsCount(@NotNull final PsiExpression expression, @Nullable final String resourceBundleName) {
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
    final TreeSet<Integer> paramsCount = new TreeSet<>();
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
