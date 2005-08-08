/*
 * Copyright 2003-2005 Dave Griffith
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.psiutils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ExpectedTypeUtils{
    /**
     * @noinspection StaticCollection
     */
    private static final Set<IElementType> arithmeticOps =
		    new HashSet<IElementType>(5);

	static {
        arithmeticOps.add(JavaTokenType.PLUS);
        arithmeticOps.add(JavaTokenType.MINUS);
        arithmeticOps.add(JavaTokenType.ASTERISK);
        arithmeticOps.add(JavaTokenType.DIV);
        arithmeticOps.add(JavaTokenType.PERC);
    }

    private ExpectedTypeUtils(){
        super();
    }

    @Nullable
    public static PsiType findExpectedType(PsiExpression exp,
                                           boolean calculateTypeForComplexReferences){
        PsiElement context = exp.getParent();
        PsiExpression wrappedExp = exp;
        while(context != null && context instanceof PsiParenthesizedExpression){
            wrappedExp = (PsiExpression) context;
            context = context.getParent();
        }
        if(context == null){
            return null;
        }
        final ExpectedTypeVisitor visitor = new ExpectedTypeVisitor(wrappedExp,
                                                                    calculateTypeForComplexReferences);
        context.accept(visitor);
        return visitor.getExpectedType();
    }

    private static boolean isArithmeticOperation(IElementType sign){
        return arithmeticOps.contains(sign);
    }

    private static boolean isComparisonOperation(IElementType sign){
        return sign.equals(JavaTokenType.EQEQ)
                || sign.equals(JavaTokenType.NE)
                || sign.equals(JavaTokenType.LE)
                || sign.equals(JavaTokenType.LT)
                || sign.equals(JavaTokenType.GE)
                || sign.equals(JavaTokenType.GT);
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

    @Nullable
    private static PsiType getTypeOfParameter(JavaResolveResult result,
                                              int parameterPosition){

        final PsiMethod psiMethod = (PsiMethod) result.getElement();
        final PsiSubstitutor substitutor = result.getSubstitutor();
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
                return substitutor
                        .substitute(((PsiArrayType) lastParameter.getType())
                                .getComponentType());
            }
            return null;
        }

        final PsiParameter param = parameters[parameterPosition];
        if(param.isVarArgs()){
            return substitutor.substitute(
                    ((PsiArrayType) param.getType()).getComponentType());
        }
        return substitutor.substitute(param.getType());
    }

    @NotNull
    private static JavaResolveResult findCalledMethod(
            PsiExpressionList expList){
        final PsiElement parent = expList.getParent();
        if(parent instanceof PsiCallExpression){
            final PsiCallExpression call = (PsiCallExpression) parent;
            return call.resolveMethodGenerics();
        }
        return JavaResolveResult.EMPTY;
    }

    private static class ExpectedTypeVisitor extends PsiElementVisitor{
        private final PsiExpression wrappedExp;
        private final boolean calculateTypeForComplexReferences;
        private PsiType expectedType = null;

        ExpectedTypeVisitor(PsiExpression wrappedExp,
                            boolean calculateTypeForComplexReferences){
            super();
            this.wrappedExp = wrappedExp;
            this.calculateTypeForComplexReferences = calculateTypeForComplexReferences;
        }

        public PsiType getExpectedType(){
            return expectedType;
        }

        public void visitField(@NotNull PsiField field){
            final PsiExpression initializer = field.getInitializer();
            if(wrappedExp.equals(initializer)){
                expectedType = field.getType();
            }
        }

        public void visitVariable(@NotNull PsiVariable variable){
            expectedType = variable.getType();
        }

        public void visitArrayInitializerExpression(
                PsiArrayInitializerExpression initializer){
            final PsiType type = initializer.getType();
            if(!(type instanceof PsiArrayType))
            {
                expectedType = null;
                return;
            }
            final PsiArrayType arrayType = (PsiArrayType) type;
            expectedType = arrayType.getComponentType();
        }

        public void visitArrayAccessExpression(
                PsiArrayAccessExpression accessExpression){
            final PsiExpression indexExpression = accessExpression
                    .getIndexExpression();
            if(wrappedExp.equals(indexExpression)){
                expectedType = PsiType.INT;
            }
        }

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression binaryExp){
            final PsiJavaToken sign = binaryExp.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final PsiType type = binaryExp.getType();
            if(TypeUtils.isJavaLangString(type)){
                expectedType = null;
            } else if(isArithmeticOperation(tokenType)){
                expectedType = type;
            } else if(isComparisonOperation(tokenType)){
                final PsiExpression lhs = binaryExp.getLOperand();
                final PsiType lhsType = lhs.getType();
                if(ClassUtils.isPrimitive(lhsType)){
                    expectedType = lhsType;
                    return;
                }
                final PsiExpression rhs = binaryExp.getROperand();
                if(rhs == null){
                    expectedType = null;
                    return;
                }
                final PsiType rhsType = rhs.getType();
                if(ClassUtils.isPrimitive(rhsType)){
                    expectedType = rhsType;
                    return;
                }
                expectedType = null;
            } else{
                expectedType = null;
            }
        }

        public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression){
            expectedType = expression.getType();
        }

        public void visitPostfixExpression(
                @NotNull PsiPostfixExpression expression){
            expectedType = expression.getType();
        }

        public void visitWhileStatement(
                @NotNull PsiWhileStatement whileStatement){
            expectedType = PsiType.BOOLEAN;
        }

        public void visitForStatement(@NotNull PsiForStatement statement){
            expectedType = PsiType.BOOLEAN;
        }

        public void visitIfStatement(@NotNull PsiIfStatement statement){
            expectedType = PsiType.BOOLEAN;
        }

        public void visitDoWhileStatement(
                @NotNull PsiDoWhileStatement statement){
            expectedType = PsiType.BOOLEAN;
        }

        public void visitSynchronizedStatement(
                @NotNull PsiSynchronizedStatement statement){
            final PsiManager manager = statement.getManager();
            final Project project = manager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            expectedType = PsiType.getJavaLangObject(manager, scope);
        }

        public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            final PsiExpression rExpression = assignment.getRExpression();
            if(rExpression != null){
                if(rExpression.equals(wrappedExp)){
                    final PsiExpression lExpression =
                            assignment.getLExpression();
                    final PsiType lType = lExpression.getType();
                    if(lType == null){
                        expectedType = null;
                    } else if(TypeUtils.isJavaLangString(lType) &&
                            JavaTokenType.PLUSEQ.equals(
                                    assignment.getOperationSign()
                                            .getTokenType())){
                        // e.g. String += any type
                        expectedType = rExpression.getType();
                    } else{
                        expectedType = lType;
                    }
                }
            }
        }

        public void visitConditionalExpression(
                PsiConditionalExpression conditional){
            final PsiExpression condition = conditional.getCondition();
            if(condition.equals(wrappedExp)){
                expectedType = PsiType.BOOLEAN;
            } else{
                expectedType = conditional.getType();
            }
        }

        public void visitReturnStatement(
                @NotNull PsiReturnStatement returnStatement){
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(returnStatement,
                                                PsiMethod.class);
            if(method == null){
                expectedType = null;
            } else{
                expectedType = method.getReturnType();
            }
        }

        public void visitDeclarationStatement(
                PsiDeclarationStatement declaration){
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            for(PsiElement declaredElement : declaredElements){
                if(declaredElement instanceof PsiVariable){
                    final PsiVariable variable =
                            (PsiVariable) declaredElement;
                    final PsiExpression initializer =
                            variable.getInitializer();
                    if(wrappedExp.equals(initializer)){
                        expectedType = variable.getType();
                        return;
                    }
                }
            }
        }

        public void visitExpressionList(PsiExpressionList expList){
            final JavaResolveResult result = findCalledMethod(expList);
            final PsiMethod method = (PsiMethod) result.getElement();
            if(method == null){
                expectedType = null;
            } else{
                final int parameterPosition =
                        getParameterPosition(expList, wrappedExp);
                expectedType = getTypeOfParameter(result, parameterPosition);
            }
        }

        public void visitReferenceExpression(
                @NotNull PsiReferenceExpression ref){
            //Dave, do we need this at all?
            if(calculateTypeForComplexReferences){
                final PsiManager manager = ref.getManager();
                final JavaResolveResult resolveResult = ref
                        .advancedResolve(false);
                final PsiElement element = resolveResult.getElement();
                PsiSubstitutor substitutor = resolveResult.getSubstitutor();
                if(element instanceof PsiField){
                    final PsiField field = (PsiField) element;
                    final PsiClass aClass = field.getContainingClass();
                    final PsiElementFactory factory = manager
                            .getElementFactory();
                    expectedType = factory.createType(aClass, substitutor);
                } else if(element instanceof PsiMethod){
                    final PsiMethod method = (PsiMethod) element;
                    final PsiMethod superMethod =
                            findDeepestVisibleSuperMethod(method, ref);
                    final PsiClass aClass;
                    if(superMethod != null){
                        aClass = superMethod.getContainingClass();
                        substitutor =
                                TypeConversionUtil.getSuperClassSubstitutor(
                                        superMethod.getContainingClass(),
                                        method.getContainingClass(),
                                        substitutor);
                    } else{
                        aClass = method.getContainingClass();
                    }
                    final PsiElementFactory factory = manager
                            .getElementFactory();
                    expectedType = factory.createType(aClass, substitutor);
                } else{
                    expectedType = null;
                }
            }
        }

        @Nullable
        private PsiMethod findDeepestVisibleSuperMethod(PsiMethod method, PsiElement element){
            if(method.isConstructor()){
                return null;
            }
            if(method.hasModifierProperty(PsiModifier.STATIC)){
                return null;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE)){
                return null;
            }

            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return null;
            }

            final PsiClass referencingClass =
                    PsiTreeUtil.getParentOfType(element, PsiClass.class);
            if(referencingClass == null)
            {
                return null;
            }
            final PsiMethod[] allMethods = aClass.getAllMethods();
            PsiMethod topSuper = null;
            for(PsiMethod superMethod : allMethods){
                final PsiClass superClass=superMethod.getContainingClass();
                if(!isVisibleFrom(superMethod, referencingClass))
                {
                    continue;
                }
                if(superClass.equals(aClass)){
                    continue;
                }
                PsiSubstitutor superClassSubstitutor = TypeConversionUtil
                        .getClassSubstitutor(superClass, aClass,
                                             PsiSubstitutor.EMPTY);
                if(superClassSubstitutor
                        == null){
                    superClassSubstitutor = PsiSubstitutor.EMPTY;
                }
                final boolean looksLikeSuperMethod =
                        method.getName().equals(superMethod.getName()) &&
                                !superMethod
                                        .hasModifierProperty(PsiModifier.STATIC)
                                &&
                                PsiUtil.isAccessible(superMethod, aClass,
                                                     aClass) &&
                                method.getSignature(PsiSubstitutor.EMPTY)
                                        .equals(superMethod.getSignature(
                                                superClassSubstitutor));
                if(looksLikeSuperMethod){
                    if(topSuper != null &&
                            superClass.isInheritor(topSuper.getContainingClass(), true)){
                        continue;
                    }
                    topSuper = superMethod;
                }
            }
            return topSuper;
        }

        private boolean isVisibleFrom(PsiMethod method, PsiClass referencingClass){
            final PsiClass containingClass = method.getContainingClass();
            if(referencingClass.equals(containingClass))
            {
                return true;
            }
            if(containingClass == null)
            {
                return false;
            }
            if(method.hasModifierProperty(PsiModifier.PUBLIC))
            {
                return true;
            }
            if(method.hasModifierProperty(PsiModifier.PRIVATE))
            {
                return false;
            }
            //if(method.hasModifierProperty(PsiModifier.PROTECTED))
            //{
            //    return referencingClass.isInheritor(containingClass, true) ||
            //            ClassUtils.inSamePackage(containingClass, referencingClass);
            //}
            return ClassUtils.inSamePackage(containingClass, referencingClass);
        }

    }
}

