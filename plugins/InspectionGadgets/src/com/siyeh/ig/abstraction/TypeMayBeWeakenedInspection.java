package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Query;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.VariableInspection;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class TypeMayBeWeakenedInspection extends VariableInspection {

    public String getDisplayName() {
        return "Type can be weakened";
    }

    public String getGroupDisplayName() {
        return GroupNames.ABSTRACTION_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final PsiClass aClass = (PsiClass) infos[0];
        return "Type of variable <code>#ref</code> can be weakened to " +
                aClass.getQualifiedName();
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TypeCanBeWeakenedVisitor();
    }
    
    private static class TypeCanBeWeakenedVisitor extends BaseInspectionVisitor {

        public void visitLocalVariable(PsiLocalVariable variable) {
            System.out.println("visitLocalVariable(" + variable + ")");
            super.visitLocalVariable(variable);
            final PsiType variableType = variable.getType();
            if (!(variableType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType variableClassType = (PsiClassType) variableType;
            final PsiClass variableClass = variableClassType.resolve();

            final PsiManager manager = variable.getManager();
            final GlobalSearchScope scope = variable.getResolveScope();
            PsiClass weakestType =
                    manager.findClass("java.lang.Object", scope);
            final Query<PsiReference> query = ReferencesSearch.search(variable);
            final Collection<PsiReference> references = query.findAll();
            for (PsiReference reference : references) {
                if (weakestType == variableClass) {
                    return;
                }
                final PsiElement referenceElement = reference.getElement();
                final PsiElement referenceParent = referenceElement.getParent();
                if (referenceParent instanceof PsiMethodCallExpression) {
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression) referenceParent;
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    final PsiElement methodElement = methodExpression.resolve();
                    if (!(methodElement instanceof PsiMethod)) {
                        return;
                    }
                    final PsiMethod method = (PsiMethod) methodElement;
                    final PsiClass containingClass =
                            method.getContainingClass();
                    if (containingClass == weakestType) {
                        return;
                    }
                    if (containingClass.isInheritor(weakestType, true)) {
                        weakestType = containingClass;
                    }
                } else if (referenceParent instanceof PsiExpressionList) {
                    final PsiExpressionList expressionList =
                            (PsiExpressionList) referenceParent;
                    final PsiElement expressionListParent =
                            expressionList.getParent();
                    if (!(expressionListParent instanceof
                            PsiMethodCallExpression)) {
                        return;
                    }
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression) expressionListParent;
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    final PsiElement methodElement = methodExpression.resolve();
                    if (!(methodElement instanceof PsiMethod)) {
                        return;
                    }
                    final PsiMethod method = (PsiMethod) methodElement;
                    final PsiParameterList parameterList =
                            method.getParameterList();
                    final PsiParameter[] parameters =
                            parameterList.getParameters();
                    if (!(referenceElement instanceof PsiExpression)) {
                        return;
                    }
                    final PsiExpression expression =
                            (PsiExpression) referenceElement;
                    final int index =
                            getExpressionIndex(expression, expressionList);
                    final PsiParameter parameter = parameters[index];
                    final PsiType type = parameter.getType();
                    if (!(type instanceof PsiClassType)) {
                        return;
                    }
                    final PsiClassType classType = (PsiClassType) type;
                    final PsiClass aClass = classType.resolve();
                    if (aClass == null || weakestType == aClass) {
                        return;
                    }
                    if (aClass.isInheritor(weakestType, true)) {
                        weakestType = aClass;
                    }
                } else if (referenceParent instanceof PsiAssignmentExpression) {
                    final PsiAssignmentExpression assignmentExpression =
                            (PsiAssignmentExpression) referenceParent;
                    final PsiExpression lhs =
                            assignmentExpression.getLExpression();
                    final PsiExpression rhs =
                            assignmentExpression.getRExpression();
                    if (referenceElement == lhs && rhs != null) {
                        final PsiType type = rhs.getType();
                        if (!(type instanceof PsiClassType)) {
                            return;
                        }
                        final PsiClassType classType = (PsiClassType) type;
                        final PsiClass aClass = classType.resolve();
                        if (aClass == null || weakestType == aClass) {
                            return;
                        }
                        if (aClass.isInheritor(weakestType, true)) {
                            weakestType = aClass;
                        }
                    } else if (referenceElement == rhs) {
                        final PsiType type = lhs.getType();
                        if (!(type instanceof PsiClassType)) {
                            return;
                        }
                        final PsiClassType classType = (PsiClassType) type;
                        final PsiClass aClass = classType.resolve();
                        if (aClass == null || weakestType == aClass) {
                            return;
                        }
                        if (aClass.isInheritor(weakestType, true)) {
                            weakestType = aClass;
                        }
                    }

                }
            }
            if (weakestType == variableClass) {
                return;
            }
            registerVariableError(variable, weakestType);
        }

        private static int getExpressionIndex(PsiExpression expression,
                                              PsiExpressionList expressionList) {
            final PsiExpression[] expressions = expressionList.getExpressions();
            for (int i = 0; i < expressions.length; i++) {
                final PsiExpression anExpression = expressions[i];
                if (anExpression == expression) {
                    return i;
                }
            }
            return -1;
        }
    }
}
