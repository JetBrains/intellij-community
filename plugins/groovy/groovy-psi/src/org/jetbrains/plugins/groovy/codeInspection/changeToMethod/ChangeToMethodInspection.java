// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations.Transformation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression;

import static com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
import static org.jetbrains.plugins.groovy.codeInspection.changeToMethod.transformations.Transformations.*;

/**
 * Replaces operator call with explicit method call.
 * GetAt and PutAt already covered by IndexedExpressionConversionIntention
 */
public class ChangeToMethodInspection extends BaseInspection {

  @NotNull
  @Override
  protected BaseInspectionVisitor buildVisitor() {
    return new BaseInspectionVisitor() {
      @Override
      public void visitExpression(@NotNull GrExpression expression) {
        Transformation<?> transformation = getTransformation(expression);
        if (transformation == null) return;
        PsiElement highlightingElement = getHighlightingElement(expression);
        if (highlightingElement == null) return;
        if (transformation.couldApplyRow(expression)) {
          registerError(
            highlightingElement,
            GroovyBundle.message("replace.with.method.message", transformation.getMethod()),
            new LocalQuickFix[]{getFix(transformation)},
            GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }

  @Nullable
  protected GroovyFix getFix(@NotNull final Transformation<?> transformation) {
    return new TransformationBasedFix(transformation);
  }

  private static class TransformationBasedFix extends GroovyFix {

    @SafeFieldForPreview
    private final Transformation<?> myTransformation;

    private TransformationBasedFix(Transformation<?> transformation) { myTransformation = transformation; }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyBundle.message("replace.with.method.fix", myTransformation.getMethod());
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) throws IncorrectOperationException {
      PsiElement call = descriptor.getPsiElement().getParent();
      if (!(call instanceof GrExpression)) return;
      if (!myTransformation.couldApplyRow((GrExpression)call)) return;

      myTransformation.applyRow((GrExpression)call);
    }
  }

  @Nullable
  public Transformation<? extends GrExpression> getTransformation(@NotNull GrExpression expression) {
    if (expression instanceof GrUnaryExpression) {
      return UNARY_TRANSFORMATIONS.get(((GrUnaryExpression)expression).getOperationTokenType());
    }

    if (expression instanceof GrBinaryExpression) {
      return BINARY_TRANSFORMATIONS.get(((GrBinaryExpression)expression).getOperationTokenType());
    }

    if (expression instanceof GrSafeCastExpression) {
      return AS_TYPE_TRANSFORMATION;
    }
    return null;
  }

  @Nullable
  public PsiElement getHighlightingElement(@NotNull GrExpression expression) {
    if (expression instanceof GrUnaryExpression) {
      return ((GrUnaryExpression)expression).getOperationToken();
    }

    if (expression instanceof GrBinaryExpression) {
      return ((GrBinaryExpression)expression).getOperationToken();
    }

    if (expression instanceof GrSafeCastExpression) {
      return ((GrSafeCastExpression)expression).getOperationToken();
    }
    return null;
  }
}