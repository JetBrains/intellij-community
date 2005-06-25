package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class VariableAccessUtils{
    private VariableAccessUtils(){
        super();
    }

    public static boolean variableIsAssignedFrom(@NotNull PsiVariable variable,
                                                 PsiElement context){
        final VariableAssignedFromVisitor visitor = new VariableAssignedFromVisitor(variable);
        context.accept(visitor);
        return visitor.isAssignedFrom();
    }

    public static boolean variableIsPassedAsMethodArgument(@NotNull PsiVariable variable,
                                                           @NotNull PsiElement context){
        final VariablePassedAsArgumentVisitor visitor = new VariablePassedAsArgumentVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsUsedInArrayInitializer(@NotNull PsiVariable variable,
                                                           @NotNull PsiElement context){
        final VariableUsedInArrayInitializerVisitor visitor = new VariableUsedInArrayInitializerVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsAssigned(@NotNull PsiVariable variable,
                                             @NotNull PsiElement context){
        final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsReturned(@NotNull PsiVariable variable,
                                             @NotNull PsiElement context){
        final VariableReturnedVisitor visitor = new VariableReturnedVisitor(variable);
        context.accept(visitor);
        return visitor.isReturned();
    }

    public static boolean arrayContentsAreAccessed(@NotNull PsiVariable variable,
                                                   @NotNull PsiElement context){
        final ArrayContentsAccessedVisitor visitor =
                new ArrayContentsAccessedVisitor(variable);
        context.accept(visitor);
        return visitor.isAccessed();
    }

    public static boolean arrayContentsAreAssigned(@NotNull PsiVariable variable,
                                                   @NotNull PsiElement context){
        final ArrayContentsAssignedVisitor visitor =
                new ArrayContentsAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsUsedInInnerClass(@NotNull PsiLocalVariable variable,
                                                     @NotNull PsiElement context){
        final VariableUsedInInnerClassVisitor visitor =
                new VariableUsedInInnerClassVisitor(variable);
        context.accept(visitor);
        return visitor.isUsedInInnerClass();
    }

    public static boolean mayEvaluateToVariable(PsiExpression expression,
                                                PsiVariable variable){
        if(expression == null)
        {
            return false;
        }
        if(expression instanceof PsiParenthesizedExpression)
        {
            final PsiExpression containedExpression =
                    ((PsiParenthesizedExpression) expression) .getExpression();
            return mayEvaluateToVariable(containedExpression, variable);
        }
        if(expression instanceof PsiConditionalExpression)
        {
            final PsiConditionalExpression conditional = (PsiConditionalExpression) expression;
            final PsiExpression thenExpression = conditional.getThenExpression();
            final PsiExpression elseExpression = conditional.getElseExpression();
            return mayEvaluateToVariable(thenExpression, variable) || mayEvaluateToVariable(elseExpression, variable);
        }
        if(!(expression instanceof PsiReferenceExpression)){
            return false;
        }
        final PsiElement referent = ((PsiReference) expression).resolve();
        if(referent == null){
            return false;
        }
        return referent.equals(variable);
    }

    public static boolean variableIsUsed(PsiElement statement,
                                         PsiVariable variable) {

        final VariableUsedVisitor visitor
                = new VariableUsedVisitor(variable);
        statement.accept(visitor);
        return visitor.isUsed();
    }
}
