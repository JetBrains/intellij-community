// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.GroovyQuickFixFactory;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTupleAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrCallExpression;

import static org.jetbrains.plugins.groovy.lang.typing.TuplesKt.getMultiAssignmentTypesCountCS;

public abstract class GroovyStaticTypeCheckVisitorBase extends GroovyTypeCheckVisitor {

  @Override
  public final void visitElement(@NotNull GroovyPsiElement element) {
    // do nothing & disable recursion
  }

  @Override
  protected abstract void registerError(@NotNull PsiElement location,
                                        @InspectionMessage @NotNull String description,
                                        LocalQuickFix @Nullable [] fixes,
                                        @NotNull ProblemHighlightType highlightType);

  @Override
  public void visitTupleAssignmentExpression(@NotNull GrTupleAssignmentExpression expression) {
    super.visitTupleAssignmentExpression(expression);
    final GrExpression initializer = expression.getRValue();
    if (initializer != null) {
      checkTupleAssignment(initializer, expression.getLValue().getExpressions().length);
    }
  }

  @Override
  public void visitVariableDeclaration(@NotNull GrVariableDeclaration variableDeclaration) {
    if (variableDeclaration.isTuple()) {
      GrExpression initializer = variableDeclaration.getTupleInitializer();
      if (initializer != null) {
        checkTupleAssignment(initializer, variableDeclaration.getVariables().length);
      }
    }
    super.visitVariableDeclaration(variableDeclaration);
  }

  private void checkTupleAssignment(@NotNull GrExpression initializer, int leftCount) {
    Integer componentCount = getMultiAssignmentTypesCountCS(initializer);
    if (componentCount == null) {
      registerError(
        initializer,
        GroovyBundle.message("multiple.assignments.without.list.expr"),
        new LocalQuickFix[]{GroovyQuickFixFactory.getInstance().createMultipleAssignmentFix(leftCount)},
        ProblemHighlightType.GENERIC_ERROR
      );
    }
    else if (componentCount < leftCount) {
      registerError(
        initializer,
        GroovyBundle.message("incorrect.number.of.values", leftCount, componentCount),
        LocalQuickFix.EMPTY_ARRAY,
        ProblemHighlightType.GENERIC_ERROR
      );
    }
  }

  @Override
  public void visitSpreadArgument(@NotNull GrSpreadArgument spreadArgument) {
    registerError(
      spreadArgument,
      GroovyBundle.message("spread.operator.is.not.available"),
      buildSpreadArgumentFix(spreadArgument),
      ProblemHighlightType.GENERIC_ERROR
    );
  }

  private static LocalQuickFix[] buildSpreadArgumentFix(GrSpreadArgument spreadArgument) {
    GrCallExpression parent = PsiTreeUtil.getParentOfType(spreadArgument, GrCallExpression.class);
    if (parent == null) return LocalQuickFix.EMPTY_ARRAY;
    PsiMethod resolveMethod = parent.resolveMethod();

    if (resolveMethod == null) return LocalQuickFix.EMPTY_ARRAY;

    return new LocalQuickFix[]{GroovyQuickFixFactory.getInstance().createSpreadArgumentFix(resolveMethod.getParameters().length)};
  }
}
