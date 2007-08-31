package com.siyeh.ig.visibility;

import com.siyeh.ig.BaseInspectionVisitor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

class AnonymousClassVariableHidesOuterClassVariableVisitor
        extends BaseInspectionVisitor {

    public void visitAnonymousClass(PsiAnonymousClass aClass) {
        super.visitAnonymousClass(aClass);
        final PsiCodeBlock codeBlock =
                PsiTreeUtil.getParentOfType(aClass, PsiCodeBlock.class);
        if (codeBlock == null) {
            return;
        }
        final VariableCollector collector = new VariableCollector();
        aClass.acceptChildren(collector);
        final PsiStatement[] statements = codeBlock.getStatements();
        final int offset = aClass.getTextOffset();
        for (PsiStatement statement : statements) {
            if (statement.getTextOffset() >= offset) {
                break;
            }
            if (!(statement instanceof PsiDeclarationStatement)) {
                continue;
            }
            final PsiDeclarationStatement declarationStatement =
                    (PsiDeclarationStatement) statement;
            final PsiElement[] declaredElements =
                    declarationStatement.getDeclaredElements();
            for (PsiElement declaredElement : declaredElements) {
                if (!(declaredElement instanceof PsiLocalVariable)) {
                    continue;
                }
                final PsiLocalVariable localVariable =
                        (PsiLocalVariable) declaredElement;
                final String name = localVariable.getName();
                final PsiVariable[] variables =
                        collector.getVariables(name);
                for (PsiVariable variable : variables) {
                    registerVariableError(variable, variable);
                }
            }
        }
        final PsiMethod containingMethod =
                PsiTreeUtil.getParentOfType(codeBlock, PsiMethod.class);
        if (containingMethod == null) {
            return;
        }
        final PsiParameterList parameterList =
                containingMethod.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        for (PsiParameter parameter : parameters) {
            final String name = parameter.getName();
            final PsiVariable[] variables = collector.getVariables(name);
            for (PsiVariable variable : variables) {
                registerVariableError(variable, variable);
            }
        }
    }

  private static class VariableCollector extends PsiRecursiveElementVisitor {

        private static final PsiVariable[] EMPTY_VARIABLE_LIST = {};

        private Map<String, List<PsiVariable>> variableMap = new HashMap();

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            final String name = variable.getName();
            final List<PsiVariable> variableList = variableMap.get(name);
            if (variableList == null) {
                final List<PsiVariable> list = new ArrayList();
                list.add(variable);
                variableMap.put(name, list);
            } else {
                variableList.add(variable);
            }
        }

        public void visitClass(PsiClass aClass) {
            // don't drill down in classes
        }

        public PsiVariable[] getVariables(String name) {
            final List<PsiVariable> variableList = variableMap.get(name);
            if (variableList == null) {
                return EMPTY_VARIABLE_LIST;
            } else {
                return variableList.toArray(
                        new PsiVariable[variableList.size()]);
            }
        }
    }
}