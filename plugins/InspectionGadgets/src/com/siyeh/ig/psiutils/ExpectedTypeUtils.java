package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;

public class ExpectedTypeUtils{
    private ExpectedTypeUtils(){
        super();
    }

    public static PsiType findExpectedType(PsiExpression exp){
        PsiElement context = exp.getParent();
        PsiExpression wrappedExp = exp;
        final PsiManager manager = exp.getManager();
        while(context instanceof PsiParenthesizedExpression){
            wrappedExp = (PsiExpression) context;
            context = context.getParent();
        }
        if(context instanceof PsiField){
            final PsiField field = (PsiField) context;
            final PsiExpression initializer = field.getInitializer();
            if(wrappedExp.equals(initializer)){
                return field.getType();
            }
        } else if(context instanceof PsiVariable){
            final PsiVariable psiVariable = (PsiVariable) context;
            return psiVariable.getType();
        } else if(context instanceof PsiReferenceExpression){
            final PsiReferenceExpression ref = (PsiReferenceExpression) context;
            final PsiElement parent = ref.getParent();
            if(parent instanceof PsiMethodCallExpression){
                final PsiMethod psiMethod =
                        ((PsiCall) parent).resolveMethod();
                if(psiMethod == null){
                    return null;
                }
                final PsiClass aClass = psiMethod.getContainingClass();
                final PsiElementFactory factory = manager.getElementFactory();
                return factory.createType(aClass);
            } else if(parent instanceof PsiReferenceExpression){
                final PsiElement elt =
                        ((PsiReference) parent).resolve();
                if(elt instanceof PsiField){
                    final PsiClass aClass =
                            ((PsiMember) elt).getContainingClass();
                    final PsiElementFactory factory =
                            manager.getElementFactory();
                    return factory.createType(aClass);
                } else{
                    return null;
                }
            }
        } else if(context instanceof PsiArrayInitializerExpression){
            final PsiArrayInitializerExpression initializer =
                    (PsiArrayInitializerExpression) context;
            final PsiArrayType arrayType = (PsiArrayType) initializer.getType();
            if(arrayType != null){
                return arrayType.getComponentType();
            }
        } else if(context instanceof PsiArrayAccessExpression){
            final PsiArrayAccessExpression accessExpression =
                    (PsiArrayAccessExpression) context;
            if(wrappedExp.equals(accessExpression.getIndexExpression())){
                return PsiType.INT;
            }
        } else if(context instanceof PsiAssignmentExpression){
            final PsiAssignmentExpression assignment =
                    (PsiAssignmentExpression) context;
            final PsiExpression rExpression = assignment.getRExpression();
            if(rExpression != null){
                if(rExpression.equals(wrappedExp)){
                    final PsiExpression lExpression =
                            assignment.getLExpression();
                    PsiType lType = lExpression.getType();
                    if(lType == null){
                        return null;
                    }
                    // e.g. String += any type
                    if(TypeUtils.isJavaLangString(lType) &&
                            JavaTokenType.PLUSEQ.equals(
                                    assignment.getOperationSign()
                                            .getTokenType())){
                        return rExpression.getType();
                    }
                    return lType;
                }
            }
        } else if(context instanceof PsiDeclarationStatement){
            final PsiDeclarationStatement assignment =
                    (PsiDeclarationStatement) context;
            final PsiElement[] declaredElements =
                    assignment.getDeclaredElements();
            for(int i = 0; i < declaredElements.length; i++){
                if(declaredElements[i] instanceof PsiVariable){
                    final PsiVariable declaredElement =
                            (PsiVariable) declaredElements[i];
                    final PsiExpression initializer =
                            declaredElement.getInitializer();
                    if(wrappedExp.equals(initializer)){
                        return declaredElement.getType();
                    }
                }
            }
        } else if(context instanceof PsiBinaryExpression){
            final PsiBinaryExpression binaryExp = (PsiBinaryExpression) context;
            final PsiJavaToken sign = binaryExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final PsiType type = binaryExp.getType();
            if(TypeUtils.isJavaLangString(type)){
                return null;
            }
            if(isArithmeticOperation(tokenType)){
                return type;
            } else if(isEqualityOperation(tokenType)){
                final PsiExpression lhs = binaryExp.getLOperand();
                if(lhs == null){
                    return null;
                }
                final PsiType lhsType = lhs.getType();
                if(ClassUtils.isPrimitive(lhsType)){
                    return lhsType;
                }
                final PsiExpression rhs = binaryExp.getROperand();
                if(rhs == null){
                    return null;
                }
                final PsiType rhsType = rhs.getType();
                if(ClassUtils.isPrimitive(rhsType)){
                    return rhsType;
                }
                return null;
            } else{
                return null;
            }
        } else if(context instanceof PsiPrefixExpression){
            final PsiPrefixExpression prefixExp = (PsiPrefixExpression) context;
            return prefixExp.getType();
        } else if(context instanceof PsiPostfixExpression){
            final PsiPostfixExpression postfixExp =
                    (PsiPostfixExpression) context;
            return postfixExp.getType();
        } else if(context instanceof PsiConditionalExpression){
            final PsiConditionalExpression conditional =
                    (PsiConditionalExpression) context;
            final PsiExpression condition = conditional.getCondition();
            if(condition.equals(wrappedExp)){
                return PsiType.BOOLEAN;
            }
            return conditional.getType();
        } else if(context instanceof PsiExpressionList){
            final PsiExpressionList expList = (PsiExpressionList) context;
            final PsiMethod method =
                    ExpectedTypeUtils.findCalledMethod(expList);
            if(method == null){
                return null;
            }
            final int parameterPosition =
                    ExpectedTypeUtils.getParameterPosition(expList, wrappedExp);
            return ExpectedTypeUtils.getTypeOfParemeter(method,
                                                        parameterPosition);
        } else if(context instanceof PsiReturnStatement){
            final PsiReturnStatement psiReturnStatement =
                    (PsiReturnStatement) context;
            final PsiMethod method = (PsiMethod) PsiTreeUtil.getParentOfType(
                    psiReturnStatement, PsiMethod.class);
            if(method == null){
                return null;
            }
            return method.getReturnType();
        } else if(context instanceof PsiWhileStatement){
            return PsiType.BOOLEAN;
        } else if(context instanceof PsiDoWhileStatement){
            return PsiType.BOOLEAN;
        } else if(context instanceof PsiForStatement){
            return PsiType.BOOLEAN;
        } else if(context instanceof PsiIfStatement){
            return PsiType.BOOLEAN;
        } else if(context instanceof PsiSynchronizedStatement){
            final Project project = manager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            return PsiClassType.getJavaLangObject(manager, scope);
        }
        return null;
    }

    private static boolean isArithmeticOperation(IElementType sign){
        return sign.equals(JavaTokenType.PLUS)
                || sign.equals(JavaTokenType.MINUS)
                || sign.equals(JavaTokenType.ASTERISK)
                || sign.equals(JavaTokenType.DIV) ||
                sign.equals(JavaTokenType.PERC);
    }

    private static boolean isEqualityOperation(IElementType sign){
        return sign.equals(JavaTokenType.EQEQ)
                || sign.equals(JavaTokenType.NE);
    }

    private static int getParameterPosition(PsiExpressionList expressionList,
                                            PsiExpression exp){
        final PsiExpression[] expressions = expressionList.getExpressions();
        for(int i = 0; i < expressions.length; i++){
            if(expressions[i].equals(exp)){
                return i;
            }
        }
        return -1;
    }

    private static PsiType getTypeOfParemeter(PsiMethod psiMethod,
                                              int parameterPosition){
        final PsiParameterList paramList = psiMethod.getParameterList();
        final PsiParameter[] parameters = paramList.getParameters();
        if(parameterPosition < 0){
            return null;
        }
        if(parameterPosition >= parameters.length){
            final int lastParamPosition = parameters.length - 1;
            if(lastParamPosition < 0){
                return null;
            }
            final PsiParameter lastParameter = parameters[lastParamPosition];
            if(lastParameter.isVarArgs()){
                return ((PsiArrayType) lastParameter.getType()).getComponentType();
            }
            return null;
        }

        final PsiParameter param = parameters[parameterPosition];
        if(param.isVarArgs()){
            return ((PsiArrayType) param.getType()).getComponentType();
        }
        return param.getType();
    }

    private static PsiMethod findCalledMethod(PsiExpressionList expList){
        final PsiElement parent = expList.getParent();
        if(parent instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression methodCall =
                    (PsiMethodCallExpression) parent;
            return methodCall.resolveMethod();
        } else if(parent instanceof PsiNewExpression){
            final PsiNewExpression psiNewExpression = (PsiNewExpression) parent;
            return psiNewExpression.resolveMethod();
        }
        return null;
    }
}
