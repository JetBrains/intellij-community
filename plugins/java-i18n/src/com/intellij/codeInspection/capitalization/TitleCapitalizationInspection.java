// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.capitalization;

import com.intellij.codeInspection.*;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.ChoiceFormat;
import java.text.Format;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TitleCapitalizationInspection extends AbstractBaseJavaLocalInspectionTool {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethod(PsiMethod method) {
        PsiType type = method.getReturnType();
        if (!InheritanceUtil.isInheritor(type, CommonClassNames.JAVA_LANG_STRING)) return;
        Collection<PsiReturnStatement> statements = PsiTreeUtil.findChildrenOfType(method, PsiReturnStatement.class);
        Nls.Capitalization capitalization = null;
        for (PsiReturnStatement returnStatement : statements) {
          PsiExpression expression = returnStatement.getReturnValue();
          if (expression == null) continue;
          List<PsiExpression> children = ExpressionUtils.nonStructuralChildren(expression).collect(Collectors.toList());
          for (PsiExpression e : children) {
            if (capitalization == null) {
              capitalization = NlsCapitalizationUtil.getCapitalizationFromAnno(method);
              if (capitalization == Nls.Capitalization.NotSpecified) return;
            }
            Value titleValue = getTitleValue(e, new HashSet<>());
            if (titleValue == null) continue;
            checkCapitalization(e, titleValue, holder, capitalization);
          }
        }
      }

      @Override
      public void visitCallExpression(PsiCallExpression expression) {
        PsiMethod psiMethod = expression.resolveMethod();
        if (psiMethod != null) {
          PsiExpressionList argumentList = expression.getArgumentList();
          if (argumentList != null) {
            PsiExpression[] args = argumentList.getExpressions();
            PsiParameter[] parameters = psiMethod.getParameterList().getParameters();
            for (int i = 0; i < Math.min(parameters.length, args.length); i++) {
              PsiParameter parameter = parameters[i];
              Nls.Capitalization capitalization = NlsCapitalizationUtil.getCapitalizationFromAnno(parameter);
              if (capitalization == Nls.Capitalization.NotSpecified) continue;
              ExpressionUtils.nonStructuralChildren(args[i])
                .forEach(e -> checkCapitalization(e, getTitleValue(e, new HashSet<>()), holder, capitalization));
            }
          }
        }
      }
    };
  }

  private static void checkCapitalization(PsiExpression e,
                                          Value titleValue,
                                          @NotNull ProblemsHolder holder,
                                          Nls.Capitalization capitalization) {
    if (titleValue != null && !titleValue.isSatisfied(capitalization)) {
      holder.registerProblem(e, "String '" + titleValue + "' is not properly capitalized. It should have " +
                                StringUtil.toLowerCase(capitalization.toString()) + " capitalization",
                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                             titleValue.canFix() ? new TitleCapitalizationFix(titleValue, capitalization) : null);
    }
  }

  @Nullable
  private static Value getTitleValue(@Nullable PsiExpression arg, Set<? super PsiElement> processed) {
    if (arg instanceof PsiLiteralExpression) {
      return Value.of((PsiLiteralExpression)arg);
    }
    if (arg instanceof PsiMethodCallExpression) {
      PsiMethod psiMethod = ((PsiMethodCallExpression)arg).resolveMethod();
      PsiExpression returnValue = PropertyUtilBase.getGetterReturnExpression(psiMethod);
      if (arg == returnValue) {
        return null;
      }
      if (returnValue != null && processed.add(returnValue)) {
        return getTitleValue(returnValue, processed);
      }
      return Value.of(getPropertyArgument((PsiMethodCallExpression)arg));
    }
    if (arg instanceof PsiReferenceExpression) {
      PsiElement result = ((PsiReferenceExpression)arg).resolve();
      if (result instanceof PsiVariable && ((PsiVariable)result).hasModifierProperty(PsiModifier.FINAL)) {
        PsiExpression initializer = ((PsiVariable)result).getInitializer();
        if (processed.add(initializer)) {
          return getTitleValue(initializer, processed);
        }
      }
    }
    return null;
  }

  @Nullable
  private static Property getPropertyArgument(PsiMethodCallExpression arg) {
    PsiExpression[] args = arg.getArgumentList().getExpressions();
    if (args.length > 0) {
      PsiReference[] references = args[0].getReferences();
      for (PsiReference reference : references) {
        if (reference instanceof PropertyReference) {
          ResolveResult[] resolveResults = ((PropertyReference)reference).multiResolve(false);
          if (resolveResults.length == 1 && resolveResults[0].isValidResult()) {
            PsiElement element = resolveResults[0].getElement();
            if (element instanceof Property) {
              return (Property) element;
            }
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
      return "Properly capitalize '" + myTitleValue + '\'';
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
          factory.createExpressionFromText('"' + value.fixCapitalization(myCapitalization) + '"', element);
        element.replace(newExpression);
      }
      else if (element instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)element;
        final PsiMethod method = methodCallExpression.resolveMethod();
        final PsiExpression returnValue = PropertyUtilBase.getGetterReturnExpression(method);
        if (returnValue != null) {
          doFix(project, returnValue);
        }
        final Property property = getPropertyArgument(methodCallExpression);
        Value value = Value.of(property);
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

    @NotNull
    @Override
    public String getFamilyName() {
      return "Properly capitalize";
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

    @Contract("null -> null")
    @Nullable
    static Value of(@Nullable Property property) {
      if (property == null) return null;
      String value = property.getUnescapedValue();
      if (value == null) return null;
      try {
        MessageFormat format = new MessageFormat(value);
        return new PropertyValue(value, format);
      }
      catch (IllegalArgumentException e) {
        return new TextValue(value);
      }
    }

    @Nullable
    static Value of(@NotNull PsiLiteralExpression literal) {
      Object value = literal.getValue();
      return value instanceof String ? new TextValue((String)value) : null;
    }
  }

  static class TextValue implements Value {
    private final String myText;

    TextValue(String text) { myText = text; }

    @NotNull @Override
    public String toString() { return myText;}

    @Override
    public boolean isSatisfied(@NotNull Nls.Capitalization capitalization) {
      return NlsCapitalizationUtil.isCapitalizationSatisfied(myText, capitalization);
    }
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
      if (!NlsCapitalizationUtil.isCapitalizationSatisfied(clone.toPattern(), capitalization)) return false;
      for (Format format : formats) {
        if (format instanceof ChoiceFormat) {
          for (Object subValue : ((ChoiceFormat)format).getFormats()) {
            String str = subValue.toString();
            if (capitalization == Nls.Capitalization.Sentence) {
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
}
