package com.siyeh.ig.psiutils;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

public class VariableSearchUtils {
    private VariableSearchUtils() {
        super();
    }

    public static boolean existsLocalOrParameter(String varName, PsiElement expression) {
        if (existsParameter(varName, expression)) {
            return true;
        }
        if (existsLocal(varName, expression)) {
            return true;
        }
        if (existsForLoopLocal(varName, expression)) {
            return true;
        }
        return existsForeachLoopLocal(varName, expression);
    }

    private static boolean existsParameter(String varName, PsiElement element) {
        PsiMethod ancestor =
                PsiTreeUtil.getParentOfType(element,
                                                        PsiMethod.class);
        while (ancestor != null) {
            final PsiParameterList paramList = ancestor.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            for(final PsiParameter parameter : parameters){
                final String paramName = parameter.getName();
                if(paramName.equals(varName)){
                    return true;
                }
            }
            ancestor = PsiTreeUtil.getParentOfType(ancestor,
                                                               PsiMethod.class);
        }
        return false;
    }

    private static boolean existsLocal(String varName, PsiElement element) {
        PsiCodeBlock ancestor = PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        while (ancestor != null) {
            final PsiStatement[] statements = ancestor.getStatements();
            for(final PsiStatement statement : statements){
                if(statement instanceof PsiDeclarationStatement){
                    final PsiDeclarationStatement decl = (PsiDeclarationStatement) statement;
                    final PsiElement[] elements = decl.getDeclaredElements();
                    for(PsiElement element1 : elements){
                        if(!(element1 instanceof PsiLocalVariable)){
                            continue;
                        }
                        final PsiLocalVariable localVar = (PsiLocalVariable) element1;
                        final String localVarName = localVar.getName();
                        if(localVarName.equals(varName)){
                            return true;
                        }
                    }
                }
            }
            ancestor = PsiTreeUtil.getParentOfType(ancestor, PsiCodeBlock.class);
        }
        return false;
    }

    private static boolean existsForLoopLocal(String varName, PsiElement element) {
        PsiForStatement forLoopAncestor = PsiTreeUtil.getParentOfType(element, PsiForStatement.class);
        while (forLoopAncestor != null) {
            final PsiStatement initialization = forLoopAncestor.getInitialization();

            if (initialization instanceof PsiDeclarationStatement) {
                final PsiDeclarationStatement decl = (PsiDeclarationStatement) initialization;
                final PsiElement[] elements = decl.getDeclaredElements();
                for(PsiElement element1 : elements){
                    final PsiLocalVariable localVar = (PsiLocalVariable) element1;
                    final String localVarName = localVar.getName();
                    if(localVarName.equals(varName)){
                        return true;
                    }
                }
            }
            forLoopAncestor = PsiTreeUtil.getParentOfType(forLoopAncestor, PsiForStatement.class);
        }
        return false;
    }

    private static boolean existsForeachLoopLocal(String varName, PsiElement element) {
        PsiForeachStatement forLoopAncestor = PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
        while (forLoopAncestor != null) {
            final PsiParameter parameter = forLoopAncestor.getIterationParameter();

            if (parameter.getName().equals(varName)) {
                return true;
            }
            forLoopAncestor = PsiTreeUtil.getParentOfType(forLoopAncestor, PsiForeachStatement.class);
        }
        return false;
    }
}
