// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.template.macro.MacroUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.util.UastExpressionUtils;

import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.*;

public class JavaI18nUtil extends I18nUtil {
  public static final PropertyCreationHandler DEFAULT_PROPERTY_CREATION_HANDLER =
    (project, propertiesFiles, key, value, parameters) -> createProperty(project, propertiesFiles, key, value, true);

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
    if (psiElement == null || psiElement instanceof PsiWhiteSpace) return null;
    return psiElement.getTextRange();
  }

  public static boolean mustBePropertyKey(@NotNull PsiExpression expression, @Nullable Ref<? super PsiAnnotationMemberValue> resourceBundleRef) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    if (parent instanceof PsiVariable) {
      final PsiAnnotation annotation = AnnotationUtil.findAnnotation((PsiVariable)parent, AnnotationUtil.PROPERTY_KEY);
      if (annotation != null) {
        processAnnotationAttributes(resourceBundleRef, annotation);
        return true;
      }
    }
    return isPassedToResourceParam(expression, resourceBundleRef);
  }

  public static boolean mustBePropertyKey(@NotNull UExpression expression, @Nullable Ref<? super UExpression> resourceBundleRef) {
    while (expression.getUastParent() instanceof UParenthesizedExpression) {
      expression = (UParenthesizedExpression)expression.getUastParent();
    }
    final UElement parent = expression.getUastParent();
    if (parent instanceof UVariable) {
      UAnnotation annotation = ((UVariable)parent).findAnnotation(AnnotationUtil.PROPERTY_KEY);
      if (annotation != null) {
        processAnnotationAttributes(resourceBundleRef, annotation);
        return true;
      }
    }

    UCallExpression callExpression = UastUtils.getUCallExpression(expression);
    if (callExpression == null) return false;
    PsiMethod psiMethod = callExpression.resolve();
    if (psiMethod == null) return false;
    PsiParameter parameter = UastUtils.getParameterForArgument(callExpression, expression);
    if (parameter == null) return false;
    int paramIndex = ArrayUtil.indexOf(psiMethod.getParameterList().getParameters(), parameter);
    if (paramIndex == -1) return false;
    if (resourceBundleRef == null) {
      return isPropertyKeyParameter(psiMethod, paramIndex, null, null);
    }
    @Nullable Ref<PsiAnnotationMemberValue> ref = new Ref<>();
    boolean isAnnotated = isPropertyKeyParameter(psiMethod, paramIndex, null, ref);
    PsiAnnotationMemberValue memberValue = ref.get();
    if (memberValue != null) {
      resourceBundleRef.set(UastContextKt.toUElementOfExpectedTypes(memberValue, UExpression.class));
    }
    return isAnnotated;
  }

  static boolean isPassedToResourceParam(@NotNull PsiExpression expression,
                                         @Nullable Ref<? super PsiAnnotationMemberValue> resourceBundleRef) {
    expression = getTopLevelExpression(expression);
    final PsiElement parent = expression.getParent();
    if (!(parent instanceof PsiExpressionList)) return false;
    int idx = ArrayUtil.indexOf(((PsiExpressionList)parent).getExpressions(), expression);
    if (idx == -1) return false;

    PsiElement grParent = parent.getParent();

    if (grParent instanceof PsiAnonymousClass) {
      grParent = grParent.getParent();
    }

    if (grParent instanceof PsiCall) {
      PsiMethod method = ((PsiCall)grParent).resolveMethod();
      return method != null && isPropertyKeyParameter(method, idx, null, resourceBundleRef);
    }

    return false;
  }

  @NotNull
  static PsiExpression getTopLevelExpression(@NotNull PsiExpression expression) {
    while (expression.getParent() instanceof PsiExpression) {
      final PsiExpression parent = (PsiExpression)expression.getParent();
      if (parent instanceof PsiConditionalExpression &&
          ((PsiConditionalExpression)parent).getCondition() == expression) {
        break;
      }
      expression = parent;
      if (expression instanceof PsiAssignmentExpression) break;
    }
    return expression;
  }

  @NotNull
  static UExpression getTopLevelExpression(@NotNull UExpression expression) {
    while (expression.getUastParent() instanceof UExpression) {
      final UExpression parent = (UExpression)expression.getUastParent();
      if (parent instanceof UBlockExpression || parent instanceof UReturnExpression) {
        break;
      }
      if (parent instanceof UIfExpression &&
          UastUtils.isPsiAncestor(((UIfExpression)parent).getCondition(), expression)) {
        break;
      }
      expression = parent;
      if (UastExpressionUtils.isAssignment(expression)) break;
    }
    return expression;
  }

  static boolean isPropertyKeyParameter(final PsiMethod method,
                                        final int idx,
                                        @Nullable Collection<? super PsiMethod> processed,
                                        @Nullable Ref<? super PsiAnnotationMemberValue> resourceBundleRef) {
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
      PsiParameter lastParam = ArrayUtil.getLastElement(params);
      if (lastParam == null || !lastParam.isVarArgs()) return false;
      param = lastParam;
    }
    else {
      param = params[idx];
    }
    final PsiAnnotation annotation = AnnotationUtil.findAnnotation(param, AnnotationUtil.PROPERTY_KEY);
    if (annotation != null) {
      processAnnotationAttributes(resourceBundleRef, annotation);
      return true;
    }

    final PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      if (isPropertyKeyParameter(superMethod, idx, processed, resourceBundleRef)) return true;
    }

    return false;
  }

  private static void processAnnotationAttributes(@Nullable Ref<? super PsiAnnotationMemberValue> resourceBundleRef,
                                                  @NotNull PsiAnnotation annotation) {
    if (resourceBundleRef != null) {
      final PsiAnnotationParameterList parameterList = annotation.getParameterList();
      final PsiNameValuePair[] attributes = parameterList.getAttributes();
      for (PsiNameValuePair attribute : attributes) {
        final String name = attribute.getName();
        if (AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER.equals(name)) {
          resourceBundleRef.set(attribute.getValue());
        }
      }
    }
  }

  private static void processAnnotationAttributes(@Nullable Ref<? super UExpression> resourceBundleRef,
                                                  @NotNull UAnnotation annotation) {
    if (resourceBundleRef != null) {
      for (UNamedExpression attribute : annotation.getAttributeValues()) {
        final String name = attribute.getName();
        if (AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER.equals(name)) {
          resourceBundleRef.set(attribute.getExpression());
        }
      }
    }
  }

  static boolean isValidPropertyReference(@NotNull Project project,
                                          @NotNull PsiExpression expression,
                                          @NotNull String key,
                                          @NotNull Ref<? super String> outResourceBundle) {
    Ref<PsiAnnotationMemberValue> resourceBundleRef = Ref.create();
    if (mustBePropertyKey(expression, resourceBundleRef)) {
      final Object resourceBundleName = resourceBundleRef.get();
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
          return bundleRef.get().equals(propertiesFile.getResourceBundle());
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
      PsiIdentifier identifier = var.getNameIdentifier();
      if ((type == null || type.isAssignableFrom(varType)) && identifier != null) {
        result.add(identifier.getText());
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

  private static void addAvailableMethodsOfType(final PsiClassType type,
                                                final PsiLiteralExpression context,
                                                final Collection<? super String> result) {
    PsiScopesUtil.treeWalkUp((element, state) -> {
      if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        PsiType returnType = method.getReturnType();
        if (returnType != null && TypeConversionUtil.isAssignable(type, returnType)
            && method.getParameterList().isEmpty()) {
          result.add(method.getName() + "()");
        }
      }
      return true;
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
      return countFormatParameters(new MessageFormat(propertyValue));
    }
    catch (final IllegalArgumentException e) {
      return 0;
    }
  }

  private static int countFormatParameters(MessageFormat mf) {
    Format[] formats = mf.getFormatsByArgumentIndex();
    int maxLength = formats.length;
    for (Format format : formats) {
      if (format instanceof ChoiceFormat) {
        for (Object o : ((ChoiceFormat) format).getFormats()) {
          maxLength = Math.max(maxLength, countFormatParameters(new MessageFormat((String) o)));
        }
      }
    }
    return maxLength;
  }

  /**
   * Returns number of different parameters in i18n message. For example, for string
   *
   * <i>Class {0} info: Class {0} extends class {1} and implements interface {2}</i> in one translation of property
   * <i>Class {0} info: Class {0} extends class {1} </i> in other translation of property
   * <p>
   * number of parameters is 3.
   *
   * @param expression i18n literal
   * @return number of parameters
   */
  public static int getPropertyValueParamsMaxCount(@NotNull final UExpression expression) {
    final SortedSet<Integer> paramsCount = getPropertyValueParamsCount(expression, null);
    if (paramsCount.isEmpty()) {
      return -1;
    }
    return paramsCount.last();
  }

  @NotNull
  static SortedSet<Integer> getPropertyValueParamsCount(@NotNull final PsiExpression expression,
                                                        @Nullable final String resourceBundleName) {
    UExpression uExpression = UastContextKt.toUElement(expression, UExpression.class);
    if (uExpression == null) return new TreeSet<>();
    return getPropertyValueParamsCount(uExpression, resourceBundleName);
  }

  @NotNull
  private static SortedSet<Integer> getPropertyValueParamsCount(@NotNull final UExpression expression,
                                                                @Nullable final String resourceBundleName) {
    final ULiteralExpression literalExpression;
    if (expression instanceof ULiteralExpression) {
      literalExpression = (ULiteralExpression)expression;
    }
    else if (expression instanceof UReferenceExpression) {
      final PsiElement resolved = ((UReferenceExpression)expression).resolve();
      final PsiField field = resolved == null ? null : (PsiField)resolved;
      literalExpression =
        field != null && field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() instanceof PsiLiteralExpression
        ? UastContextKt.toUElement(field.getInitializer(), ULiteralExpression.class)
        : null;
    }
    else {
      literalExpression = null;
    }
    final TreeSet<Integer> paramsCount = new TreeSet<>();
    if (literalExpression == null) {
      return paramsCount;
    }
    for (PsiReference reference : UastLiteralUtils.getInjectedReferences(literalExpression)) {
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
