package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;

public class VariableAccessUtils {
    private VariableAccessUtils() {
        super();
    }

    public static boolean variableIsAssignedFrom(PsiVariable variable, PsiElement context) {
        final VariableAssignedFromVisitor visitor = new VariableAssignedFromVisitor(variable);
        context.accept(visitor);
        return visitor.isAssignedFrom();
    }

    public static boolean variableIsPassedAsMethodArgument(PsiVariable variable, PsiElement context) {
        final VariablePassedAsArgumentVisitor visitor = new VariablePassedAsArgumentVisitor(variable);
        context.accept(visitor);
        return visitor.isPassed();
    }

    public static boolean variableIsAssigned(PsiVariable variable, PsiElement context) {
        final VariableAssignedVisitor visitor = new VariableAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsReturned(PsiVariable variable, PsiElement context) {
        final VariableReturnedVisitor visitor = new VariableReturnedVisitor(variable);
        context.accept(visitor);
        return visitor.isReturned();
    }

    public static boolean arrayContentsAreAccessed(PsiVariable variable, PsiElement context) {
        final ArrayContentsAccessedVisitor visitor = new ArrayContentsAccessedVisitor(variable);
        context.accept(visitor);
        return visitor.isAccessed();
    }

    public static boolean arrayContentsAreAssigned(PsiVariable variable, PsiElement context) {
        final ArrayContentsAssignedVisitor visitor = new ArrayContentsAssignedVisitor(variable);
        context.accept(visitor);
        return visitor.isAssigned();
    }

    public static boolean variableIsUsedInInnerClass(PsiLocalVariable variable, PsiElement context) {
        final VariableUsedInInnerClassVisitor visitor =
                new VariableUsedInInnerClassVisitor(variable);
        context.accept(visitor);
        return visitor.isUsedInInnerClass();
    }
}
