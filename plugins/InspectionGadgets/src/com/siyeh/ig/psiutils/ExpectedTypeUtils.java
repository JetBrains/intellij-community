package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

public class ExpectedTypeUtils {

    private ExpectedTypeUtils() {
        super();
    }

    public static PsiType findExpectedType(PsiExpression exp) {
        PsiElement context = exp.getParent();
        PsiExpression wrappedExp = exp;
        final PsiManager manager = exp.getManager();
        while (context instanceof PsiParenthesizedExpression) {
            wrappedExp = (PsiExpression) context;
            context = context.getParent();
        }
        if (context instanceof PsiVariable) {
            final PsiVariable psiVariable = (PsiVariable) context;
            return psiVariable.getType();
        } else if (context instanceof PsiReferenceExpression) {
            final PsiReferenceExpression ref = (PsiReferenceExpression) context;
            final PsiElement parent = ref.getParent();
            if (parent instanceof PsiMethodCallExpression) {
                final PsiMethod psiMethod = ((PsiMethodCallExpression) parent).resolveMethod();
                if (psiMethod == null) {
                    return null;
                }
                final PsiClass aClass = psiMethod.getContainingClass();
                final PsiElementFactory factory = manager.getElementFactory();
                return factory.createType(aClass);
            } else if (parent instanceof PsiReferenceExpression) {
                final PsiElement elt = ((PsiReferenceExpression) parent).resolve();
                if (elt instanceof PsiField) {
                    final PsiClass aClass = ((PsiField) elt).getContainingClass();
                    final PsiElementFactory factory = manager.getElementFactory();
                    return factory.createType(aClass);
                } else {
                    return null;
                }
            }

        } else if (context instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression) context;
            final PsiExpression rExpression = assignment.getRExpression();
            if (rExpression != null) {
                if (rExpression.equals(wrappedExp)) {
                    final PsiExpression lExpression = assignment.getLExpression();
                    return lExpression.getType();
                }
            }
        } else if (context instanceof PsiBinaryExpression) {
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression) context;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            final PsiType type = binaryExp.getType();
            if (TypeUtils.isJavaLangString(type)) {
                return null;
            }
            if (isArithmeticOperation(sign)) {
                return type;
            } else {
                return null;
            }
        } else if (context instanceof PsiPrefixExpression) {
            final PsiPrefixExpression prefixExp = (PsiPrefixExpression) context;
            return prefixExp.getType();
        } else if (context instanceof PsiPostfixExpression) {
            final PsiPostfixExpression postfixExp = (PsiPostfixExpression) context;
            return postfixExp.getType();
        } else if (context instanceof PsiConditionalExpression) {
            final PsiConditionalExpression conditional = (PsiConditionalExpression) context;
            final PsiExpression condition = conditional.getCondition();
            if (condition.equals(wrappedExp)) {
                return PsiType.BOOLEAN;
            }
            return conditional.getType();
        } else if (context instanceof PsiExpressionList) {
            final PsiExpressionList expList = (PsiExpressionList) context;
            final PsiMethod method = ExpectedTypeUtils.findCalledMethod(expList);
            if (method == null) {
                return null;
            }
            final int parameterPosition = ExpectedTypeUtils.getParameterPosition(expList, wrappedExp);
            return ExpectedTypeUtils.getTypeOfParemeter(method, parameterPosition);
        } else if (context instanceof PsiReturnStatement) {
            final PsiReturnStatement psiReturnStatement = (PsiReturnStatement) context;
            final PsiMethod method = ExpectedTypeUtils.findEnclosingPsiMethod(psiReturnStatement);
            if (method == null) {
                return null;
            }
            return method.getReturnType();
        } else if (context instanceof PsiWhileStatement) {
            return PsiType.BOOLEAN;
        } else if (context instanceof PsiDoWhileStatement) {
            return PsiType.BOOLEAN;
        } else if (context instanceof PsiForStatement) {
            return PsiType.BOOLEAN;
        } else if (context instanceof PsiIfStatement) {
            return PsiType.BOOLEAN;
        } else if (context instanceof PsiSynchronizedStatement) {
            final Project project = manager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            return PsiClassType.getJavaLangObject(manager, scope);
        }
        return null;
    }

    private static boolean isArithmeticOperation(PsiJavaToken sign) {
        return sign.equals(JavaTokenType.PLUS)
                || sign.equals(JavaTokenType.MINUS)
                || sign.equals(JavaTokenType.ASTERISK)
                || sign.equals(JavaTokenType.DIV) ||
                sign.equals(JavaTokenType.PERC);
    }

    private static int getParameterPosition(PsiExpressionList expressionList, PsiExpression exp) {
        final PsiExpression[] expressions = expressionList.getExpressions();
        for (int i = 0; i < expressions.length; i++) {
            if (expressions[i].equals(exp)) {
                return i;
            }
        }
        return -1;
    }

    private static PsiType getTypeOfParemeter(PsiMethod psiMethod, int parameterPosition) {
        final PsiParameterList paramList = psiMethod.getParameterList();
        final PsiParameter[] parameters = paramList.getParameters();
        if (parameterPosition >= parameters.length || parameterPosition < 0) {
            return null;
        }

        final PsiParameter param = parameters[parameterPosition];
        return param.getType();
    }

    private static PsiMethod findEnclosingPsiMethod(PsiElement psiElement) {
        PsiElement currentPsiElement = psiElement;
        while (currentPsiElement != null) {
            currentPsiElement = currentPsiElement.getParent();
            if (currentPsiElement instanceof PsiMethod) {
                return (PsiMethod) currentPsiElement;
            }
        }
        return null;
    }

    private static PsiMethod findCalledMethod(PsiExpressionList expList) {
        final PsiElement parent = expList.getParent();
        if (parent instanceof PsiMethodCallExpression) {
            final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) parent;
            final PsiMethod method = methodCall.resolveMethod();
            return method;
        } else if (parent instanceof PsiNewExpression) {
            final PsiNewExpression psiNewExpression = (PsiNewExpression) parent;
            return psiNewExpression.resolveMethod();
        }
        return null;
    }

}
