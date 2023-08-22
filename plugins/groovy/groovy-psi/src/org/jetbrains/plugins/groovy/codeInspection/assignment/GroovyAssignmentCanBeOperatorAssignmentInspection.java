/*
 * Copyright 2007-2008 Dave Griffith
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
package org.jetbrains.plugins.groovy.codeInspection.assignment;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspection;
import org.jetbrains.plugins.groovy.codeInspection.BaseInspectionVisitor;
import org.jetbrains.plugins.groovy.codeInspection.GroovyFix;
import org.jetbrains.plugins.groovy.codeInspection.utils.EquivalenceChecker;
import org.jetbrains.plugins.groovy.codeInspection.utils.SideEffectChecker;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class GroovyAssignmentCanBeOperatorAssignmentInspection
    extends BaseInspection {

  /**
   * @noinspection PublicField,WeakerAccess
   */
  public boolean ignoreLazyOperators = true;

  /**
   * @noinspection PublicField,WeakerAccess
   */
  public boolean ignoreObscureOperators = false;

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final GrAssignmentExpression assignmentExpression =
      (GrAssignmentExpression)infos[0];
    return GroovyBundle
      .message("inspection.message.assignment.replaceable.with.operator.assignment", calculateReplacementExpression(assignmentExpression));
  }

  @Override
  public @NotNull OptPane getGroovyOptionsPane() {
    return pane(
      checkbox("ignoreLazyOperators", GroovyBundle.message("checkbox.ignore.conditional.operators")),
      checkbox("ignoreObscureOperators", GroovyBundle.message("checkbox.ignore.obscure.operators")));
  }

  static String calculateReplacementExpression(
      GrAssignmentExpression expression) {
    final GrExpression rhs = expression.getRValue();
    final GrBinaryExpression binaryExpression =
        (GrBinaryExpression)PsiUtil.skipParentheses(rhs, false);
    final GrExpression lhs = expression.getLValue();
    assert binaryExpression != null;
    final IElementType sign = binaryExpression.getOperationTokenType();
    final GrExpression rhsRhs = binaryExpression.getRightOperand();
    assert rhsRhs != null;
    String signText = getTextForOperator(sign);
    if ("&&".equals(signText)) {
      signText = "&";
    } else if ("||".equals(signText)) {
      signText = "|";
    }
    return lhs.getText() + ' ' + signText + "= " + rhsRhs.getText();
  }

  @NotNull
  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ReplaceAssignmentWithOperatorAssignmentVisitor();
  }

  @Override
  public GroovyFix buildFix(@NotNull PsiElement location) {
    return new ReplaceAssignmentWithOperatorAssignmentFix(
        (GrAssignmentExpression) location);
  }

  private static final class ReplaceAssignmentWithOperatorAssignmentFix
      extends GroovyFix {

    private final @IntentionName String m_name;

    private ReplaceAssignmentWithOperatorAssignmentFix(
        GrAssignmentExpression expression) {
      super();
      final GrExpression rhs = expression.getRValue();
      final GrBinaryExpression binaryExpression =
          (GrBinaryExpression)PsiUtil.skipParentheses(rhs, false);
      assert binaryExpression != null;
      final IElementType sign = binaryExpression.getOperationTokenType();
      String signText = getTextForOperator(sign);
      if ("&&".equals(signText)) {
        signText = "&";
      } else if ("||".equals(signText)) {
        signText = "|";
      }
      m_name = GroovyBundle.message("intention.name.replace.eq.with.0.eq", signText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return GroovyBundle.message("intention.family.name.simplify");
    }

    @Override
    @NotNull
    public String getName() {
      return m_name;
    }

    @Override
    public void doFix(@NotNull Project project,
                      @NotNull ProblemDescriptor descriptor)
        throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof GrAssignmentExpression expression)) {
        return;
      }
      final String newExpression =
          calculateReplacementExpression(expression);
      replaceExpression(expression, newExpression);
    }
  }

  private class ReplaceAssignmentWithOperatorAssignmentVisitor
      extends BaseInspectionVisitor {

    @Override
    public void visitAssignmentExpression(@NotNull GrAssignmentExpression assignment) {
      super.visitAssignmentExpression(assignment);
      if (assignment.isOperatorAssignment()) {
        return;
      }
      final GrExpression lhs = assignment.getLValue();
      final GrExpression rhs = (GrExpression)PsiUtil.skipParentheses(assignment.getRValue(), false);
      if (!(rhs instanceof GrBinaryExpression binaryRhs)) {
        return;
      }
      if (binaryRhs.getRightOperand() == null) {
        return;
      }
      final IElementType expressionTokenType =
          binaryRhs.getOperationTokenType();
      if (getTextForOperator(expressionTokenType) == null) {
        return;
      }
      if (JavaTokenType.EQEQ.equals(expressionTokenType)) {
        return;
      }
      if (ignoreLazyOperators) {
        if (GroovyTokenTypes.mLAND.equals(expressionTokenType) ||
            GroovyTokenTypes.mLOR.equals(expressionTokenType)) {
          return;
        }
      }
      if (ignoreObscureOperators) {
        if (GroovyTokenTypes.mBXOR.equals(expressionTokenType) ||
            GroovyTokenTypes.mMOD.equals(expressionTokenType)) {
          return;
        }
      }
      final GrExpression lOperand = binaryRhs.getLeftOperand();
      if (SideEffectChecker.mayHaveSideEffects(lhs)) {
        return;
      }
      if (!EquivalenceChecker.expressionsAreEquivalent(lhs, lOperand)) {
        return;
      }
      registerError(assignment, assignment);
    }
  }

  @Nullable
  @NonNls
  private static String getTextForOperator(IElementType operator) {
    if (operator ==  null) {
      return null;
    }
    if (operator.equals(GroovyTokenTypes.mPLUS)) {
      return "+";
    }
    if (operator.equals(GroovyTokenTypes.mMINUS)) {
      return "-";
    }
    if (operator.equals(GroovyTokenTypes.mSTAR)) {
      return "*";
    }
    if (operator.equals(GroovyTokenTypes.mDIV)) {
      return "/";
    }
    if (operator.equals(GroovyTokenTypes.mMOD)) {
      return "%";
    }
    if (operator.equals(GroovyTokenTypes.mBXOR)) {
      return "^";
    }
    if (operator.equals(GroovyTokenTypes.mLAND)) {
      return "&&";
    }
    if (operator.equals(GroovyTokenTypes.mLOR)) {
      return "||";
    }
    if (operator.equals(GroovyTokenTypes.mBAND)) {
      return "&";
    }
    if (operator.equals(GroovyTokenTypes.mBOR)) {
      return "|";
    }
    /*
    if (operator.equals(GroovyTokenTypes.mSR)) {
        return "<<";
    }

    if (operator.equals(GroovyTokenTypes.GTGT)) {
        return ">>";
    }
    if (operator.equals(GroovyTokenTypes.GTGTGT)) {
        return ">>>";
    }
    */
    return null;
  }
}
