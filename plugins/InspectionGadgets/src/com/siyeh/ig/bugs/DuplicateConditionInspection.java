package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.EquivalenceChecker;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class DuplicateConditionInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Duplicate condition in 'if' statement";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONTROL_FLOW_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Duplicate condition #ref #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new DuplicateConditionVisitor();
    }

    private static class DuplicateConditionVisitor
            extends BaseInspectionVisitor{

        public void visitIfStatement(@NotNull PsiIfStatement statement){
            super.visitIfStatement(statement);
            final PsiElement parent = statement.getParent();
            if(parent instanceof PsiIfStatement){
                final PsiIfStatement parentStatement = (PsiIfStatement) parent;
                final PsiStatement elseBranch = parentStatement.getElseBranch();
                if(statement.equals(elseBranch)){
                    return;
                }
            }
            final Set<PsiExpression> conditions = new HashSet<PsiExpression>();
            collectConditionsForIfStatement(statement, conditions);
            final int numConditions = conditions.size();
            if(numConditions < 2){
                return;
            }
            final PsiExpression[] conditionArray =
                    conditions.toArray(new PsiExpression[numConditions]);
            final boolean[] matched = new boolean[conditionArray.length];
            Arrays.fill(matched, false);
            for(int i = 0; i < conditionArray.length; i++){
                if(matched[i]){
                    continue;
                }
                final PsiExpression condition = conditionArray[i];
                for(int j = i+1; j < conditionArray.length; j++){
                    if(matched[j]){
                        continue;
                    }
                    final PsiExpression testCondition = conditionArray[j];
                    final boolean areEquivalent =
                            EquivalenceChecker.expressionsAreEquivalent(condition,
                                                                                  testCondition);
                    if(areEquivalent){
                        registerError(testCondition);
                        if(!matched[i]){
                            registerError(condition);
                        }
                        matched[i] = true;
                        matched[j] = true;
                    }
                }
            }
        }

        private void collectConditionsForIfStatement(PsiIfStatement statement,
                                       Set<PsiExpression> conditions){
            final PsiExpression condition = statement.getCondition();
            collectConditionsForExpression(condition, conditions);
            final PsiStatement branch = statement.getElseBranch();
            if(branch instanceof PsiIfStatement)
            {
                collectConditionsForIfStatement((PsiIfStatement)branch, conditions);
            }
        }

        private void collectConditionsForExpression(PsiExpression condition, Set<PsiExpression> conditions){
            if(condition == null)
            {
                return;
            }
            if(condition instanceof PsiParenthesizedExpression)
            {
                final PsiExpression contents = ((PsiParenthesizedExpression) condition).getExpression();
                collectConditionsForExpression(contents, conditions);
                return;
            }
            if(condition instanceof PsiBinaryExpression)
            {
                final PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
                final PsiJavaToken sign = binaryExpression.getOperationSign();
                if(sign!=null)
                {
                    final IElementType tokenType = sign.getTokenType();
                    if(JavaTokenType.OROR.equals(tokenType))
                    {
                        final PsiExpression lhs = binaryExpression.getLOperand();
                        collectConditionsForExpression(lhs, conditions);
                        final PsiExpression rhs = binaryExpression.getROperand();
                        collectConditionsForExpression(rhs, conditions);
                        return;
                    }
                }
            }
            conditions.add(condition);
        }
    }
}
