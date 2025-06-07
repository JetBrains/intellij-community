// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.changeToMethod;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
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
public final class ChangeToMethodInspection extends BaseInspection {

  @Override
  protected @NotNull BaseInspectionVisitor buildVisitor() {
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
            new LocalQuickFix[]{new TransformationBasedFix(transformation)},
            GENERIC_ERROR_OR_WARNING
          );
        }
      }
    };
  }

  private static class TransformationBasedFix extends PsiUpdateModCommandQuickFix {
    private final Transformation<?> myTransformation;

    private TransformationBasedFix(Transformation<?> transformation) { myTransformation = transformation; }

    @Override
    public @Nls @NotNull String getFamilyName() {
      return GroovyBundle.message("replace.with.method.fix", myTransformation.getMethod());
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement call = element.getParent();
      if (!(call instanceof GrExpression expression)) return;
      if (!myTransformation.couldApplyRow(expression)) return;

      myTransformation.applyRow(expression);
    }
  }

  public @Nullable Transformation<? extends GrExpression> getTransformation(@NotNull GrExpression expression) {
    if (expression instanceof GrUnaryExpression unary) {
      return UNARY_TRANSFORMATIONS.get(unary.getOperationTokenType());
    }

    if (expression instanceof GrBinaryExpression binary) {
      return BINARY_TRANSFORMATIONS.get(binary.getOperationTokenType());
    }

    if (expression instanceof GrSafeCastExpression) {
      return AS_TYPE_TRANSFORMATION;
    }
    return null;
  }

  public @Nullable PsiElement getHighlightingElement(@NotNull GrExpression expression) {
    if (expression instanceof GrUnaryExpression unary) {
      return unary.getOperationToken();
    }

    if (expression instanceof GrBinaryExpression binary) {
      return binary.getOperationToken();
    }

    if (expression instanceof GrSafeCastExpression safeCast) {
      return safeCast.getOperationToken();
    }
    return null;
  }
}