// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToOperator;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations.Transformation;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;
import static org.jetbrains.plugins.groovy.codeInspection.changeToOperator.transformations.Transformations.TRANSFORMATIONS;

public class ChangeToOperatorInspection extends BaseInspection {
  public boolean useDoubleNegation = true;
  public boolean shouldChangeCompareToEqualityToEquals = true;
  public boolean withoutAdditionalParentheses = false;

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull GrMethodCallExpression methodCall) {
        final GrExpression invokedExpression = methodCall.getInvokedExpression();
        if (!(invokedExpression instanceof GrReferenceExpression referenceExpression)) return;

        if (referenceExpression.getDotTokenType() != GroovyTokenTypes.mDOT) return;

        final PsiElement highlightElement = referenceExpression.getReferenceNameElement();
        if (highlightElement == null) return;

        final String methodName = getMethodName(methodCall);
        if (methodName == null) return;

        Transformation transformation = TRANSFORMATIONS.get(methodName);
        if (transformation == null) return;

        if (transformation.couldApply(methodCall, getOptions())) {
          registerError(
            highlightElement,
            GroovyBundle.message("replace.with.operator.message", methodName),
            new LocalQuickFix[]{getFix(transformation, methodName)},
            GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }

  @Nullable
  protected GroovyFix getFix(@NotNull Transformation transformation, @NotNull String methodName) {
    return new OperatorToMethodFix(transformation, methodName, getOptions());
  }

  @Nullable
  public String getMethodName(@NotNull GrMethodCall methodCall) {
    PsiMethod method = methodCall.resolveMethod();
    if (method == null || method.hasModifierProperty(PsiModifier.STATIC)) return null;
    return method.getName();
  }


  private static class OperatorToMethodFix extends GroovyFix {

    private final @SafeFieldForPreview Transformation myTransformation;

    private final String methodName;

    private final @SafeFieldForPreview Options myOptions;

    private OperatorToMethodFix(Transformation transformation, String name, Options options) {
      myTransformation = transformation;
      methodName = name;
      myOptions = options;
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyBundle.message("replace.with.operator.fix", methodName);
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement call = descriptor.getPsiElement().getParent();
      if (call == null) return;
      call = call.getParent();
      if (!(call instanceof GrMethodCall methodCall)) return;
      GrExpression invokedExpression = methodCall.getInvokedExpression();
      if (!(invokedExpression instanceof GrReferenceExpression)) return;

      if(!myTransformation.couldApply(methodCall, myOptions)) return;
      myTransformation.apply(methodCall, myOptions);
    }
  }

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      checkbox("useDoubleNegation", GroovyBundle.message("replace.with.operator.double.negation.option")),
      checkbox("shouldChangeCompareToEqualityToEquals", GroovyBundle.message("replace.with.operator.compareTo.equality.option")),
      checkbox("withoutAdditionalParentheses", GroovyBundle.message("replace.with.operator.parentheses")));
  }

  private Options getOptions() {
    return new Options(useDoubleNegation, shouldChangeCompareToEqualityToEquals, withoutAdditionalParentheses);
  }

  public static final class Options {
    private final boolean useDoubleNegation;
    private final boolean shouldChangeCompareToEqualityToEquals;
    private final boolean withoutAdditionalParentheses;

    public Options(boolean useDoubleNegation, boolean shouldChangeCompareToEqualityToEquals, boolean withoutAdditionalParentheses) {
      this.useDoubleNegation = useDoubleNegation;
      this.shouldChangeCompareToEqualityToEquals = shouldChangeCompareToEqualityToEquals;
      this.withoutAdditionalParentheses = withoutAdditionalParentheses;
    }

    public boolean useDoubleNegation() {
      return useDoubleNegation;
    }

    public boolean shouldChangeCompareToEqualityToEquals() {
      return shouldChangeCompareToEqualityToEquals;
    }

    public boolean withoutAdditionalParentheses() {
      return withoutAdditionalParentheses;
    }
  }
}