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
                (PsiMethod) PsiTreeUtil.getParentOfType(element,
                                                        PsiMethod.class);
        while (ancestor != null) {
            final PsiParameterList paramList = ancestor.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                final PsiParameter parameter = parameters[i];
                final String paramName = parameter.getName();
                if (paramName.equals(varName)) {
                    return true;
                }
            }
            ancestor = (PsiMethod) PsiTreeUtil.getParentOfType(ancestor,
                                                               PsiMethod.class);
        }
        return false;
    }

    private static boolean existsLocal(String varName, PsiElement element) {
        PsiCodeBlock ancestor = (PsiCodeBlock) PsiTreeUtil.getParentOfType(element, PsiCodeBlock.class);
        while (ancestor != null) {
            final PsiStatement[] statements = ancestor.getStatements();
            for (int i = 0; i < statements.length; i++) {
                final PsiStatement statement = statements[i];
                if (statement instanceof PsiDeclarationStatement) {
                    final PsiDeclarationStatement decl = (PsiDeclarationStatement) statement;
                    final PsiElement[] elements = decl.getDeclaredElements();
                    for (int j = 0; j < elements.length; j++) {
                        if (!(elements[j] instanceof PsiLocalVariable)) {
                            continue;
                        }
                        final PsiLocalVariable localVar = (PsiLocalVariable) elements[j];
                        final String localVarName = localVar.getName();
                        if (localVarName.equals(varName)) {
                            return true;
                        }
                    }
                }
            }
            ancestor = (PsiCodeBlock) PsiTreeUtil.getParentOfType(ancestor, PsiCodeBlock.class);
        }
        return false;
    }

    private static boolean existsForLoopLocal(String varName, PsiElement element) {
        PsiForStatement forLoopAncestor = (PsiForStatement) PsiTreeUtil.getParentOfType(element, PsiForStatement.class);
        while (forLoopAncestor != null) {
            final PsiStatement initialization = forLoopAncestor.getInitialization();

            if (initialization instanceof PsiDeclarationStatement) {
                final PsiDeclarationStatement decl = (PsiDeclarationStatement) initialization;
                final PsiElement[] elements = decl.getDeclaredElements();
                for (int j = 0; j < elements.length; j++) {
                    final PsiLocalVariable localVar = (PsiLocalVariable) elements[j];
                    final String localVarName = localVar.getName();
                    if (localVarName.equals(varName)) {
                        return true;
                    }
                }
            }
            forLoopAncestor = (PsiForStatement) PsiTreeUtil.getParentOfType(forLoopAncestor, PsiForStatement.class);
        }
        return false;
    }

    private static boolean existsForeachLoopLocal(String varName, PsiElement element) {
        PsiForeachStatement forLoopAncestor = (PsiForeachStatement) PsiTreeUtil.getParentOfType(element, PsiForeachStatement.class);
        while (forLoopAncestor != null) {
            final PsiParameter parameter = forLoopAncestor.getIterationParameter();

            if (parameter.getName().equals(varName)) {
                return true;
            }
            forLoopAncestor = (PsiForeachStatement) PsiTreeUtil.getParentOfType(forLoopAncestor, PsiForeachStatement.class);
        }
        return false;
    }
}
