package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.BoolUtils;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionEquivalenceChecker;

public class TrivialIfInspection extends ExpressionInspection{
    private final TrivialIfFix fix = new TrivialIfFix();

    public String getDisplayName(){
        return "Unnecessary 'if' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                                  boolean onTheFly){
        return new TrivialIfVisitor(this, inspectionManager, onTheFly);
    }

    public String buildErrorString(PsiElement location){
        return "#ref statement can be simplified #loc";
    }

    private static String calculateReplacementStatement(PsiIfStatement statement){
        PsiStatement thenBranch = statement.getThenBranch();
        thenBranch = ControlFlowUtils.stripBraces(thenBranch);
        final PsiExpression condition = statement.getCondition();
        final String replacementString;
        if(thenBranch instanceof PsiReturnStatement){
            if(isReturn(thenBranch, "true")){
                replacementString = "return " + condition.getText() + ';';
            } else{
                replacementString =
                        "return " +
                        BoolUtils.getNegatedExpressionText(condition) + ';';
            }
        } else{
            final PsiExpressionStatement expressionStatement =
                    (PsiExpressionStatement) thenBranch;
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) expressionStatement.getExpression();

            final PsiExpression lhs = assignment.getLExpression();
            final PsiJavaToken sign = assignment.getOperationSign();
            if(isAssignment(thenBranch, "true")){
                replacementString = lhs.getText() + ' ' +
                        sign.getText() + ' ' + condition.getText() + ';';
            } else{
                replacementString = lhs.getText() + ' ' +
                        sign.getText() + ' ' +
                        BoolUtils.getNegatedExpressionText(condition) + ';';
            }
        }
        return replacementString;
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private class TrivialIfFix extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement ifKeywordElement = descriptor.getPsiElement();
            final PsiIfStatement statement =
                    (PsiIfStatement) ifKeywordElement.getParent();
            try{
                if(isSimplifiableAssignment(statement)){
                    replaceSimplifiableAssignment(statement, project);
                } else if(isSimplifiableReturn(statement)){
                    repaceSimplifiableReturn(statement, project);
                } else if(isSimplifiableImplicitReturn(statement)){
                    replaceSimplifiableImplicitReturn(statement, project);
                } else if(isSimplifiableAssignmentNegated(statement)){
                    replaceSimplifiableAssignmentNegated(statement, project);
                } else if(isSimplifiableReturnNegated(statement)){
                    repaceSimplifiableReturnNegated(statement, project);
                } else if(isSimplifiableImplicitReturnNegated(statement)){
                    replaceSimplifiableImplicitReturnNegated(statement,
                                                             project);
                } else if(isSimplifiableImplicitAssignment(statement)){
                    replaceSimplifiableImplicitAssignment(statement, project);
                } else if(isSimplifiableImplicitAssignmentNegated(statement)){
                    replaceSimplifiableImplicitAssignmentNegated(statement,
                                                                 project);
                }
            } catch(IncorrectOperationException e){
            }
        }

        private void replaceSimplifiableImplicitReturn(PsiIfStatement statement,
                                                       Project project)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final PsiElement nextStatement =
                    PsiTreeUtil.skipSiblingsForward(statement,
                                                    new Class[]{PsiWhiteSpace.class});
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(project, statement, newStatement);
            nextStatement.delete();
        }

        private void repaceSimplifiableReturn(PsiIfStatement statement,
                                              Project project)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(project, statement, newStatement);
        }

        private void replaceSimplifiableAssignment(PsiIfStatement statement,
                                                   Project project)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(project,
                             statement,
                             lhsText + operand + conditionText + ';');
        }

        private void replaceSimplifiableImplicitAssignment(PsiIfStatement statement,
                                                           Project project)
                throws IncorrectOperationException{
            final PsiElement prevStatement =
                    PsiTreeUtil.skipSiblingsBackward(statement,
                                                     new Class[]{PsiWhiteSpace.class});

            final PsiExpression condition = statement.getCondition();
            final String conditionText = condition.getText();
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(project,
                             statement,
                             lhsText + operand + conditionText + ';');
            prevStatement.delete();
        }

        private void replaceSimplifiableImplicitAssignmentNegated(PsiIfStatement statement,
                                                                  Project project)
                throws IncorrectOperationException{
            final PsiElement prevStatement =
                    PsiTreeUtil.skipSiblingsBackward(statement,
                                                     new Class[]{PsiWhiteSpace.class});

            final PsiExpression condition = statement.getCondition();
            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(project,
                             statement,
                             lhsText + operand + conditionText + ';');
            prevStatement.delete();
        }

        private void replaceSimplifiableImplicitReturnNegated(PsiIfStatement statement,
                                                              Project project)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();

            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final PsiElement nextStatement =
                    PsiTreeUtil.skipSiblingsForward(statement,
                                                    new Class[]{PsiWhiteSpace.class});
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(project, statement, newStatement);
            nextStatement.delete();
        }

        private void repaceSimplifiableReturnNegated(PsiIfStatement statement,
                                                     Project project)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final String newStatement = "return " + conditionText + ';';
            replaceStatement(project, statement, newStatement);
        }

        private void replaceSimplifiableAssignmentNegated(PsiIfStatement statement,
                                                          Project project)
                throws IncorrectOperationException{
            final PsiExpression condition = statement.getCondition();
            final String conditionText =
                    BoolUtils.getNegatedExpressionText(condition);
            final PsiStatement thenBranch = statement.getThenBranch();
            final PsiExpressionStatement assignmentStatement =
                    (PsiExpressionStatement) ConditionalUtils.stripBraces(thenBranch);
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) assignmentStatement.getExpression();
            final PsiJavaToken operator =
                    assignmentExpression.getOperationSign();
            final String operand = operator.getText();
            final PsiExpression lhs = assignmentExpression.getLExpression();
            final String lhsText = lhs.getText();
            replaceStatement(project,
                             statement,
                             lhsText + operand + conditionText + ';');
        }
    }

    private static class TrivialIfVisitor extends BaseInspectionVisitor{
        private TrivialIfVisitor(BaseInspection inspection,
                                 InspectionManager inspectionManager,
                                 boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitIfStatement(PsiIfStatement ifStatement){
            super.visitIfStatement(ifStatement);
            if(isSimplifiableAssignment(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableReturn(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableImplicitReturn(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }
            if(isSimplifiableAssignmentNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableReturnNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableImplicitReturnNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }
            if(isSimplifiableImplicitAssignment(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }

            if(isSimplifiableImplicitAssignmentNegated(ifStatement)){
                registerStatementError(ifStatement);
                return;
            }
        }
    }

    public static boolean isSimplifiableImplicitReturn(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        final PsiStatement elseBranch = (PsiStatement) nextStatement;
        if(ConditionalUtils.isReturn(thenBranch, "true")
                   && ConditionalUtils.isReturn(elseBranch, "false")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableImplicitReturnNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);

        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsForward(ifStatement,
                                                new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        final PsiStatement elseBranch = (PsiStatement) nextStatement;
        if(ConditionalUtils.isReturn(thenBranch, "false")
                   && ConditionalUtils.isReturn(elseBranch, "true")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableReturn(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isReturn(thenBranch, "true")
                   && ConditionalUtils.isReturn(elseBranch, "false")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableReturnNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isReturn(thenBranch, "false")
                   && ConditionalUtils.isReturn(elseBranch, "true")){
            return true;
        }
        return false;
    }

    public static boolean isSimplifiableAssignment(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "true") &&
                   ConditionalUtils.isAssignment(elseBranch, "false")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return ExpressionEquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                                         elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableAssignmentNegated(PsiIfStatement ifStatement){
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        PsiStatement elseBranch = ifStatement.getElseBranch();
        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "false") &&
                   ConditionalUtils.isAssignment(elseBranch, "true")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return ExpressionEquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                                         elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignment(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                                 new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;

        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "true") &&
                   ConditionalUtils.isAssignment(elseBranch, "false")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return ExpressionEquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                                         elseLhs);
        } else{
            return false;
        }
    }

    public static boolean isSimplifiableImplicitAssignmentNegated(PsiIfStatement ifStatement){
        if(ifStatement.getElseBranch() != null){
            return false;
        }
        PsiStatement thenBranch = ifStatement.getThenBranch();
        thenBranch = ConditionalUtils.stripBraces(thenBranch);
        final PsiElement nextStatement =
                PsiTreeUtil.skipSiblingsBackward(ifStatement,
                                                 new Class[]{PsiWhiteSpace.class});
        if(!(nextStatement instanceof PsiStatement)){
            return false;
        }
        PsiStatement elseBranch = (PsiStatement) nextStatement;

        elseBranch = ConditionalUtils.stripBraces(elseBranch);
        if(ConditionalUtils.isAssignment(thenBranch, "false") &&
                   ConditionalUtils.isAssignment(elseBranch, "true")){
            final PsiAssignmentExpression thenExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) thenBranch).getExpression();
            final PsiAssignmentExpression elseExpression =
                    (PsiAssignmentExpression) ((PsiExpressionStatement) elseBranch).getExpression();
            final PsiJavaToken thenSign = thenExpression.getOperationSign();
            final PsiJavaToken elseSign = elseExpression.getOperationSign();
            if(!thenSign.getTokenType().equals(elseSign.getTokenType())){
                return false;
            }
            final PsiExpression thenLhs = thenExpression.getLExpression();
            final PsiExpression elseLhs = elseExpression.getLExpression();
            return ExpressionEquivalenceChecker.expressionsAreEquivalent(thenLhs,
                                                                         elseLhs);
        } else{
            return false;
        }
    }

    private static boolean isReturn(PsiStatement statement, String value){
        if(statement == null){
            return false;
        }
        if(!(statement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) statement;
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if(returnValue == null){
            return false;
        }
        final String returnValueString = returnValue.getText();
        return value.equals(returnValueString);
    }

    private static boolean isAssignment(PsiStatement statement, String value){
        if(statement == null){
            return false;
        }
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpression expression =
                ((PsiExpressionStatement) statement).getExpression();
        if(!(expression instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiExpression rhs =
                ((PsiAssignmentExpression) expression).getRExpression();
        if(rhs == null){
            return false;
        }
        final String rhsText = rhs.getText();
        return value.equals(rhsText);
    }

    private static boolean areCompatibleAssignments(PsiStatement statement1,
                                                    PsiStatement statement2){
        final PsiExpressionStatement expressionStatement1 =
                (PsiExpressionStatement) statement1;
        final PsiAssignmentExpression expression1 =
                (PsiAssignmentExpression) expressionStatement1.getExpression();
        final PsiExpressionStatement expressionStatement2 =
                (PsiExpressionStatement) statement2;
        final PsiAssignmentExpression expression2 =
                (PsiAssignmentExpression) expressionStatement2.getExpression();

        final PsiJavaToken sign2 = expression2.getOperationSign();
        if(sign2 == null){
            return false;
        }
        final String operand2 = sign2.getText();
        final PsiJavaToken sign1 = expression1.getOperationSign();
        if(sign1 == null){
            return false;
        }
        final String operand1 = sign1.getText();
        if(!operand2.equals(operand1)){
            return false;
        }
        final PsiExpression lhs1 = expression1.getLExpression();
        final PsiExpression lhs2 = expression2.getLExpression();
        return ExpressionEquivalenceChecker.expressionsAreEquivalent(lhs1,
                                                                     lhs2);
    }
}
