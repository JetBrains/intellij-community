// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.*;
import com.intellij.java.i18n.JavaI18nBundle;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.*;
import org.jetbrains.uast.expressions.UInjectionHost;

import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.*;

public class TitleCapitalizationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        super.visitElement(element);
        UExpression uElement =
          UastContextKt.toUElementOfExpectedTypes(element, UInjectionHost.class, UCallExpression.class, UReferenceExpression.class);
        Value titleValue = getTitleValue(uElement, new HashSet<>());
        if (titleValue == null) return;
        List<UExpression> usages = I18nInspection.findIndirectUsages(uElement, false);
        if (usages.isEmpty()) {
          usages = Collections.singletonList(uElement);
        }
        EnumSet<Nls.Capitalization> capitalizationContexts = EnumSet.noneOf(Nls.Capitalization.class);

        for (UExpression usage : usages) {
          capitalizationContexts.add(getCapitalization(usage));
        }
        capitalizationContexts.remove(Nls.Capitalization.NotSpecified);
        for (Nls.Capitalization capitalization : capitalizationContexts) {
          if (!titleValue.isSatisfied(capitalization)) {
            String message;
            LocalQuickFix fix = null;
            if (capitalizationContexts.size() > 1) {
              message = JavaI18nBundle.message("inspection.title.capitalization.mix.description");
            }
            else if (titleValue instanceof DeclaredValue) {
              message = JavaI18nBundle.message("inspection.title.capitalization.mismatch.description",
                                               titleValue, getCapitalizationName(capitalization));
            }
            else {
              fix = titleValue.canFix() && element instanceof PsiExpression ? new TitleCapitalizationFix(titleValue, capitalization) : null;
              message = JavaI18nBundle.message("inspection.title.capitalization.description",
                                               titleValue, getCapitalizationName(capitalization));
            }
            holder.registerProblem(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix);
          }
        }
      }

      private Nls.Capitalization getCapitalization(UExpression usage) {
        Nls.Capitalization capitalization = Nls.Capitalization.NotSpecified;
        NlsInfo info = NlsInfo.forExpression(usage, false);
        if (info instanceof NlsInfo.Localized) {
          capitalization = ((NlsInfo.Localized)info).getCapitalization();
        }
        else if (info.getNlsStatus() == ThreeState.UNSURE) {
          UElement parent = usage.getUastParent();
          if (usage instanceof UCallExpression && parent instanceof UQualifiedReferenceExpression) {
            parent = parent.getUastParent();
          }
          UCallExpression call = ObjectUtils.tryCast(parent, UCallExpression.class);
          if (call != null) {
            PsiMethod method = call.resolve();
            if (method != null) {
              PsiParameter parameter = AnnotationContext.getParameter(method, call, usage);
              if (parameter != null) {
                capitalization = getSupplierCapitalization(parameter);
              }
            }
          }
        }
        return capitalization;
      }
    };
  }

  private static @Nls String getCapitalizationName(Nls.Capitalization capitalization) {
    switch (capitalization) {
      case Title:
        return JavaI18nBundle.message("capitalization.kind.title");
      case Sentence:
        return JavaI18nBundle.message("capitalization.kind.sentence");
      default:
        throw new IllegalArgumentException();
    }
  }

  @Nullable
  private static Value getTitleValue(@Nullable UExpression arg, Set<? super UExpression> processed) {
    if (arg instanceof UInjectionHost) {
      return Value.of((UInjectionHost)arg);
    }
    if (arg instanceof UCallExpression) {
      UCallExpression call = (UCallExpression)arg;
      PsiMethod psiMethod = call.resolve();
      UExpression returnValue = UastContextKt.toUElement(PropertyUtilBase.getGetterReturnExpression(psiMethod), UExpression.class);
      if (returnValue instanceof UQualifiedReferenceExpression) {
        returnValue = ((UQualifiedReferenceExpression)returnValue).getSelector();
      }
      if (arg.equals(returnValue)) {
        return null;
      }
      if (returnValue != null && processed.add(returnValue)) {
        return getTitleValue(returnValue, processed);
      }
      Value fromProperty = Value.of(getPropertyArgument(call), call.getValueArgumentCount() > 1);
      if (fromProperty != null) {
        return fromProperty;
      }
    }
    if (arg instanceof UResolvable) {
      PsiElement target = ((UResolvable)arg).resolve();
      if (target instanceof PsiModifierListOwner) {
        Value value = Value.of(NlsInfo.forModifierListOwner((PsiModifierListOwner)target));
        if (value != null) {
          return value;
        }
      }
      PsiType type = arg.getExpressionType();
      if (type != null) {
        Value value = Value.of(NlsInfo.forType(type));
        if (value != null) {
          return value;
        }
      }
    }
    return null;
  }

  @Nullable
  private static Property getPropertyArgument(UCallExpression arg) {
    List<UExpression> args = arg.getValueArguments();
    if (!args.isEmpty()) {
      PsiElement psi = args.get(0).getSourcePsi();
      if (psi != null) {
        if (args.get(0).equals(UastContextKt.toUElement(psi.getParent()))) {
          // In Kotlin, we should go one level up (from KtLiteralStringTemplateEntry to KtStringTemplateExpression) 
          // to find the property reference
          psi = psi.getParent();
        }
        return getProperty(psi);
      }
    }
    return null;
  }

  @Nullable
  private static Property getProperty(PsiElement psi) {
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

  private static class TitleCapitalizationFix implements LocalQuickFix {
    private final Value myTitleValue;
    private final Nls.Capitalization myCapitalization;

    TitleCapitalizationFix(Value titleValue, Nls.Capitalization capitalization) {
      myTitleValue = titleValue;
      myCapitalization = capitalization;
    }

    @NotNull
    @Override
    public String getName() {
      return JavaI18nBundle.message("quickfix.text.title.capitalization", myTitleValue);
    }

    @Override
    public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement problemElement = descriptor.getPsiElement();
      if (problemElement == null) return;
      doFix(project, problemElement);
    }

    protected void doFix(Project project, PsiElement element) throws IncorrectOperationException {
      if (element instanceof PsiLiteralExpression) {
        Value value = Value.of((PsiLiteralExpression)element);
        if (value == null) return;
        final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        final PsiExpression newExpression =
          factory.createExpressionFromText('"' + StringUtil.escapeStringCharacters(value.fixCapitalization(myCapitalization)) + '"', element);
        element.replace(newExpression);
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression call = (PsiMethodCallExpression)element;
        final PsiMethod method = call.resolveMethod();
        final PsiExpression returnValue = PropertyUtilBase.getGetterReturnExpression(method);
        if (returnValue != null) {
          doFix(project, returnValue);
        }
        final Property property = getPropertyArgument(call);
        Value value = Value.of(property, call.getArgumentList().getExpressionCount() > 1);
        if (value == null) return;
        property.setValue(value.fixCapitalization(myCapitalization));
      }
      else if (element instanceof PsiReferenceExpression) {
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)element;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return;
        }
        final PsiVariable variable = (PsiVariable)target;
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
          doFix(project, variable.getInitializer());
        }
      }
    }

    @Nullable
    private static Property getPropertyArgument(PsiMethodCallExpression arg) {
      PsiExpression[] args = arg.getArgumentList().getExpressions();
      if (args.length > 0) {
        return getProperty(args[0]);
      }
      return null;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return JavaI18nBundle.message("quickfix.family.title.capitalization.fix");
    }
  }

  interface Value {
    @NotNull String toString();
    boolean isSatisfied(@NotNull Nls.Capitalization capitalization);

    @NotNull
    default String fixCapitalization(@NotNull Nls.Capitalization capitalization) {
      return NlsCapitalizationUtil.fixValue(toString(), capitalization);
    }

    default boolean canFix() { return true; }

    @Contract("null, _ -> null")
    @Nullable
    static Value of(@Nullable Property property, boolean useFormat) {
      if (property == null) return null;
      String value = property.getUnescapedValue();
      if (value == null) return null;
      if (useFormat) {
        try {
          MessageFormat format = new MessageFormat(value);
          return new PropertyValue(value, format);
        }
        catch (IllegalArgumentException ignore) {
        }
      }
      return new TextValue(value);
    }

    static Value of(@NotNull UInjectionHost literal) {
      String value = literal.evaluateToString();
      return value == null ? null : new TextValue(value);
    }

    @Nullable
    static Value of(@NotNull PsiLiteralExpression literal) {
      Object value = literal.getValue();
      return value instanceof String ? new TextValue((String)value) : null;
    }

    @Nullable
    static Value of(NlsInfo info) {
      if (info instanceof NlsInfo.Localized) {
        Nls.Capitalization capitalization = ((NlsInfo.Localized)info).getCapitalization();
        if (capitalization != Nls.Capitalization.NotSpecified) {
          return new DeclaredValue(capitalization);
        }
      }
      return null;
    }
  }

  static class TextValue implements Value {
    private final String myText;

    TextValue(String text) { myText = text; }

    @NotNull
    @Override
    public String toString() { return myText;}

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      return NlsCapitalizationUtil.isCapitalizationSatisfied(StringUtil.stripHtml(myText, true), capitalization);
    }
  }

  static class DeclaredValue implements Value {
    private final Nls.Capitalization myDeclared;

    DeclaredValue(Nls.Capitalization declared) {
      myDeclared = declared;
    }

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      return capitalization == Nls.Capitalization.NotSpecified || capitalization == myDeclared;
    }

    @Override
    public boolean canFix() {
      return false;
    }

    @NotNull
    @Override
    public String toString() { return getCapitalizationName(myDeclared);}
  }

  static class PropertyValue implements Value {
    private final String myPresentation;
    private final MessageFormat myFormat;

    PropertyValue(String presentation, MessageFormat format) {
      myPresentation = presentation;
      myFormat = format;
    }

    @NotNull @Override
    public String toString() {
      return myPresentation;
    }

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      if (capitalization == Nls.Capitalization.NotSpecified) return true;
      Format[] formats = myFormat.getFormats();
      MessageFormat clone = (MessageFormat)myFormat.clone();
      clone.setFormats(new Format[formats.length]);
      if (!NlsCapitalizationUtil.isCapitalizationSatisfied(StringUtil.stripHtml(clone.toPattern(), true), capitalization)) return false;
      boolean startsWithFormat = myFormat.toPattern().startsWith("{");
      for (int i = 0; i < formats.length; i++) {
        Format format = formats[i];
        if (format instanceof ChoiceFormat) {
          for (Object subValue : ((ChoiceFormat)format).getFormats()) {
            String str = subValue.toString();
            if (capitalization == Nls.Capitalization.Sentence && (i > 0 || !startsWithFormat)) {
              str = "The " + str;
            }
            if (!NlsCapitalizationUtil.isCapitalizationSatisfied(str, capitalization)) return false;
          }
        }
      }
      return true;
    }

    @Override
    public boolean canFix() {
      return ContainerUtil.findInstance(myFormat.getFormats(), ChoiceFormat.class) == null;
    }
  }

  private static Nls.@NotNull Capitalization getSupplierCapitalization(PsiParameter parameter) {
    PsiClassType classType = ObjectUtils.tryCast(parameter.getType(), PsiClassType.class);
    if (classType != null &&
        classType.equalsToText(CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER + "<" + CommonClassNames.JAVA_LANG_STRING + ">")) {
      PsiType typeParameter = ArrayUtil.getFirstElement(classType.getParameters());
      if (typeParameter != null) {
        NlsInfo info = NlsInfo.forType(typeParameter);
        if (info instanceof NlsInfo.Localized) {
          return ((NlsInfo.Localized)info).getCapitalization();
        }
      }
    }
    return Nls.Capitalization.NotSpecified;
  }
}
