// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.template.macro.MacroUtil;
import com.intellij.codeInspection.restriction.AnnotationContext;
import com.intellij.codeInspection.restriction.StringFlowUtil;
import com.intellij.lang.properties.ResourceBundle;
import com.intellij.lang.properties.*;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.PropertyCreationHandler;
import com.intellij.lang.properties.references.I18nUtil;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import kotlin.sequences.SequencesKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;
import org.jetbrains.uast.expressions.UStringConcatenationsFacade;
import org.jetbrains.uast.generate.UastCodeGenerationPlugin;
import org.jetbrains.uast.util.UastExpressionUtils;

import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.*;
import java.util.stream.Collectors;

public final class JavaI18nUtil {
  public static final PropertyCreationHandler DEFAULT_PROPERTY_CREATION_HANDLER =
    (project, propertiesFiles, key, value, parameters) -> I18nUtil.createProperty(project, propertiesFiles, key, value, true);

  public static final PropertyCreationHandler EMPTY_CREATION_HANDLER =
    (project, propertiesFiles, key, value, parameters) -> {};

  private JavaI18nUtil() {
  }

  @Nullable
  public static TextRange getSelectedRange(Editor editor, @NotNull PsiFile psiFile) {
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
    UExpression uExpression = UastContextKt.toUElement(expression, UExpression.class);
    if (uExpression == null) return false;
    Ref<UExpression> resourceBundleURef = resourceBundleRef == null ? null : Ref.create();
    if (!mustBePropertyKey(uExpression, resourceBundleURef)) return false;
    if (resourceBundleURef != null) {
      UExpression value = resourceBundleURef.get();
      if (value != null) {
        resourceBundleRef.set(ObjectUtils.tryCast(value.getSourcePsi(), PsiAnnotationMemberValue.class));
      }
    }
    return true;
  }

  public static boolean mustBePropertyKey(@NotNull UExpression expression, @Nullable Ref<? super UExpression> resourceBundleRef) {
    expression = StringFlowUtil.goUp(expression, false, NlsInfo.factory());
    AnnotationContext context = AnnotationContext.fromExpression(expression);
    return context.allItems().anyMatch(owner -> {
      PsiAnnotation annotation = owner.findAnnotation(AnnotationUtil.PROPERTY_KEY);
      if (annotation != null && resourceBundleRef != null) {
        PsiAnnotationMemberValue attributeValue = annotation.findAttributeValue(AnnotationUtil.PROPERTY_KEY_RESOURCE_BUNDLE_PARAMETER);
        resourceBundleRef.set(UastContextKt.toUElement(attributeValue, UExpression.class));
      }
      return annotation != null;
    });
  }

  @NotNull
  static UExpression getTopLevelExpression(@NotNull UExpression expression, boolean stopAtCall) {
    while (expression.getUastParent() instanceof UExpression parent) {
      if (parent instanceof UBlockExpression || parent instanceof UReturnExpression) {
        break;
      }
      if (parent instanceof UIfExpression &&
          UastUtils.isPsiAncestor(((UIfExpression)parent).getCondition(), expression)) {
        break;
      }
      expression = parent;
      if (UastExpressionUtils.isAssignment(expression)) break;
      if (expression instanceof UCallExpression && stopAtCall) {
        UastCallKind kind = ((UCallExpression)expression).getKind();
        if (kind == UastCallKind.METHOD_CALL) {
          if (expression.getUastParent() instanceof UQualifiedReferenceExpression) {
            expression = (UExpression)expression.getUastParent();
          }
          break;
        }
      }
    }
    return expression;
  }

  static boolean isValidPropertyReference(@NotNull Project project,
                                          @NotNull PsiExpression expression,
                                          @NotNull String key,
                                          @NotNull Ref<? super String> outResourceBundle) {
    Ref<PsiAnnotationMemberValue> resourceBundleRef = Ref.create();
    if (mustBePropertyKey(expression, resourceBundleRef)) {
      final Object resourceBundleName = resourceBundleRef.get();
      if (!(resourceBundleName instanceof PsiExpression expr)) {
        return false;
      }
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

  private static boolean isPropertyRef(@NotNull PsiExpression expression, @NotNull String key, @Nullable String resourceBundleName) {
    if (resourceBundleName == null) {
      return !PropertiesImplUtil.findPropertiesByKey(expression.getProject(), key).isEmpty();
    }
    List<PropertiesFile> propertiesFiles = I18nUtil.propertiesFilesByBundleName(resourceBundleName, expression);
    boolean containedInPropertiesFile = false;
    for (PropertiesFile propertiesFile : propertiesFiles) {
      containedInPropertiesFile |= propertiesFile.findPropertyByKey(key) != null;
    }
    return containedInPropertiesFile;
  }

  public static @NotNull Set<String> suggestExpressionOfType(final PsiClassType type, final PsiElement context) {
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

  private static void addAvailableMethodsOfType(@NotNull PsiClassType type,
                                                @NotNull PsiElement context,
                                                @NotNull Collection<? super String> result) {
    PsiScopesUtil.treeWalkUp((element, state) -> {
      if (element instanceof PsiMethod method) {
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

  private static int countFormatParameters(@NotNull MessageFormat mf) {
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
    final UInjectionHost injectionHost;
    if (expression instanceof UInjectionHost) {
      injectionHost = (UInjectionHost)expression;
    }
    else if (expression instanceof UReferenceExpression) {
      final PsiElement resolved = ((UReferenceExpression)expression).resolve();
      final PsiField field = resolved == null ? null : (PsiField)resolved;
      injectionHost =
        field != null && field.hasModifierProperty(PsiModifier.FINAL) && field.getInitializer() instanceof PsiLiteralExpression
        ? UastContextKt.toUElement(field.getInitializer(), UInjectionHost.class)
        : null;
    }
    else {
      injectionHost = null;
    }
    final TreeSet<Integer> paramsCount = new TreeSet<>();
    if (injectionHost == null) {
      return paramsCount;
    }
    for (PsiReference reference : UastLiteralUtils.getInjectedReferences(injectionHost)) {
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

  public static @NotNull String buildUnescapedFormatString(@NotNull UStringConcatenationsFacade cf,
                                                           @NotNull List<? super UExpression> formatParameters,
                                                           @NotNull Project project) {
    return buildUnescapedFormatString(cf, formatParameters, project, false);
  }

  private static @NotNull String buildUnescapedFormatString(@NotNull UStringConcatenationsFacade cf,
                                                            @NotNull List<? super UExpression> formatParameters,
                                                            @NotNull Project project,
                                                            boolean nested) {
    StringBuilder result = new StringBuilder();
    boolean noEscapingRequired = !nested && SequencesKt.all(cf.getUastOperands(), expression -> expression instanceof ULiteralExpression);
    for (UExpression expression : SequencesKt.asIterable(cf.getUastOperands())) {
      while (expression instanceof UParenthesizedExpression) {
        expression = ((UParenthesizedExpression)expression).getExpression();
      }
      if (expression instanceof ULiteralExpression) {
        Object value = ((ULiteralExpression)expression).getValue();
        if (value != null) {
          if (noEscapingRequired) {
            result.append(value);
          }
          else {
            String formatString = PsiConcatenationUtil.formatString(value.toString(), false);
            result.append(nested ? PsiConcatenationUtil.formatString(formatString, false) : formatString);
          }
        }
      }
      else if (nested || !addChoicePattern(expression, formatParameters, project, result)) {
        result.append("{").append(formatParameters.size()).append("}");
        formatParameters.add(expression);
      }
    }
    return result.toString();
  }

  private static boolean addChoicePattern(@NotNull UExpression expression,
                                          @NotNull List<? super UExpression> formatParameters,
                                          @NotNull Project project,
                                          @NotNull StringBuilder result) {
    if (!(expression instanceof UIfExpression)) return false;
    PsiElement sourcePsi = expression.getSourcePsi();
    if (sourcePsi == null) return false;
    UastCodeGenerationPlugin generationPlugin = UastCodeGenerationPlugin.byLanguage(sourcePsi.getLanguage());
    if (generationPlugin == null) return false;

    UExpression thenExpression = ((UIfExpression)expression).getThenExpression();
    UExpression elseExpression = ((UIfExpression)expression).getElseExpression();
    if (!(thenExpression instanceof UInjectionHost) &&
        !(elseExpression instanceof UInjectionHost)) return false;

    boolean nested = !(thenExpression instanceof UInjectionHost && elseExpression instanceof UInjectionHost);

    String thenStr = getSideText(formatParameters, project, thenExpression, nested);
    String elseStr = getSideText(formatParameters, project, elseExpression, nested);

    result.append("{")
      .append(formatParameters.size())
      .append(", choice, 0#").append(thenStr)
      .append("|1#").append(elseStr)
      .append("}");


    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    UIfExpression exCopy = UastContextKt.toUElement(sourcePsi.copy(), UIfExpression.class);
    assert exCopy != null;
    generationPlugin.replace(Objects.requireNonNull(exCopy.getThenExpression()),
                             Objects.requireNonNull(UastContextKt.toUElement(elementFactory.createExpressionFromText("0", null), ULiteralExpression.class)),
                             ULiteralExpression.class);

    generationPlugin.replace(Objects.requireNonNull(exCopy.getElseExpression()),
                             Objects.requireNonNull(UastContextKt.toUElement(elementFactory.createExpressionFromText("1", null), ULiteralExpression.class)),
                             ULiteralExpression.class);
    formatParameters.add(exCopy);
    return true;
  }

  @NotNull
  private static String getSideText(@NotNull List<? super UExpression> formatParameters,
                                    @NotNull Project project,
                                    UExpression expression,
                                    boolean nested) {
    String elseStr;
    if (expression instanceof ULiteralExpression) {
      Object elseValue = ((ULiteralExpression)expression).getValue();
      if (elseValue != null) {
        elseStr = PsiConcatenationUtil.formatString(elseValue.toString(), false);
        elseStr = nested ? PsiConcatenationUtil.formatString(elseStr, false) : elseStr;
      }
      else {
        elseStr = "null";
      }
    }
    else {
      UStringConcatenationsFacade concatenation = UStringConcatenationsFacade.createFromTopConcatenation(expression);
      if (concatenation != null) {
        elseStr = buildUnescapedFormatString(concatenation, formatParameters, project, true);
      }
      else {
        elseStr = "{" + formatParameters.size() + "}";
        formatParameters.add(expression);
      }
    }
    return elseStr.replaceAll("([<>|#])", "'$1'");
  }

  @NotNull
  static String composeParametersText(@NotNull List<? extends UExpression> args) {
    return args.stream().map(UExpression::getSourcePsi).filter(Objects::nonNull).map(psi -> psi.getText()).collect(Collectors.joining(","));
  }

  /**
   * @param expression expression that refers to the property
   * @return the resolved property; null if the property cannot be resolved
   */
  public static @Nullable Property resolveProperty(@NotNull UExpression expression) {
    PsiElement psi = expression.getSourcePsi();
    if (psi == null) return null;
    if (expression.equals(UastContextKt.toUElement(psi.getParent()))) {
      // In Kotlin, we should go one level up (from KtLiteralStringTemplateEntry to KtStringTemplateExpression)
      // to find the property reference
      psi = psi.getParent();
    }
    return resolveProperty(psi);
  }

  /**
   * @param psi expression that refers to the property
   * @return the resolved property; null if the property cannot be resolved
   */
  public static @Nullable Property resolveProperty(PsiElement psi) {
    PsiReference[] references = psi.getReferences();
    for (PsiReference reference : references) {
      if (reference instanceof PropertyReference) {
        ResolveResult[] resolveResults = ((PropertyReference)reference).multiResolve(false);
        if (resolveResults.length == 1 && resolveResults[0].isValidResult()) {
          PsiElement element = resolveResults[0].getElement();
          if (element instanceof Property) {
            return (Property)element;
          }
        }
      }
    }
    return null;
  }
}
