package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.MethodInspection;

public class WhileLoopSpinsOnFieldInspection extends MethodInspection {

    public String getDisplayName() {
        return "While loop spins on field";
    }

    public String getGroupDisplayName() {
        return GroupNames.THREADING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "#ref loop spins on field #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new WhileLoopSpinsOnFieldVisitor(this, inspectionManager, onTheFly);
    }

    private static class WhileLoopSpinsOnFieldVisitor extends BaseInspectionVisitor {
        private WhileLoopSpinsOnFieldVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitWhileStatement(PsiWhileStatement statement) {
            super.visitWhileStatement(statement);
            final PsiStatement body = statement.getBody();
            if (!statementIsEmpty(body)) {
                return;
            }
            final PsiExpression condition = statement.getCondition();
            if (!isSimpleFieldComparison(condition)) {
                return;
            }
            registerStatementError(statement);
        }

        private boolean isSimpleFieldComparison(PsiExpression condition) {
            if (condition == null) {
                return false;
            }
            if (isSimpleFieldAccess(condition)) {
                return true;
            }
            if (condition instanceof PsiPrefixExpression) {
                final PsiExpression operand =
                        ((PsiPrefixExpression) condition).getOperand();
                return isSimpleFieldComparison(operand);
            }
            if (condition instanceof PsiPostfixExpression) {
                final PsiExpression operand =
                        ((PsiPostfixExpression) condition).getOperand();
                return isSimpleFieldComparison(operand);
            }
            if (condition instanceof PsiParenthesizedExpression) {
                final PsiExpression operand =
                        ((PsiParenthesizedExpression) condition).getExpression();
                return isSimpleFieldComparison(operand);
            }

            if (condition instanceof PsiBinaryExpression) {
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression) condition;
                final PsiExpression lOperand = binaryExpression.getLOperand();
                final PsiExpression rOperand = binaryExpression.getROperand();
                return isSimpleFieldComparison(lOperand) &&
                               isLiteral(rOperand) ||
                        (isSimpleFieldComparison(rOperand) && isLiteral(lOperand));
            }
            return false;
        }

        private boolean isLiteral(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression operand =
                        ((PsiParenthesizedExpression) expression).getExpression();
                return isSimpleFieldAccess(operand);
            }
            return expression instanceof PsiLiteralExpression;
        }

        private boolean isSimpleFieldAccess(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiParenthesizedExpression) {
                final PsiExpression operand =
                        ((PsiParenthesizedExpression) expression).getExpression();
                return isSimpleFieldAccess(operand);
            }
            if (!(expression instanceof PsiReferenceExpression)) {
                return false;
            }
            final PsiElement referent = ((PsiReference) expression).resolve();
            if (!(referent instanceof PsiField)) {
                return false;
            }
            final PsiField field = (PsiField) referent;
            return !field.hasModifierProperty(PsiModifier.VOLATILE);
        }

        private boolean statementIsEmpty(PsiStatement statement) {
            if (statement == null) {
                return false;
            }
            if (statement instanceof PsiEmptyStatement) {
                return true;
            }
            if (statement instanceof PsiBlockStatement) {
                final PsiCodeBlock codeBlock =
                        ((PsiBlockStatement) statement).getCodeBlock();
                if (codeBlock == null) {
                    return false;
                }
                final PsiStatement[] statements = codeBlock.getStatements();
                if (statements == null) {
                    return false;
                }
                for(PsiStatement statement1 : statements){
                    if(!statementIsEmpty(statement1)){
                        return false;
                    }
                }
                return true;
            }
            return false;
        }
    }

}
