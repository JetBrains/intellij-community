/*
 * Copyright 2003-2011 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

public class ExpectedTypeUtils{

    private ExpectedTypeUtils() {}

    @Nullable
    public static PsiType findExpectedType(
            @NotNull PsiExpression expression,
            boolean calculateTypeForComplexReferences){
        PsiElement context = expression.getParent();
        PsiExpression wrappedExpression = expression;
        while(context != null && context instanceof PsiParenthesizedExpression){
            wrappedExpression = (PsiExpression) context;
            context = context.getParent();
        }
        if(context == null){
            return null;
        }
        final ExpectedTypeVisitor visitor =
                new ExpectedTypeVisitor(wrappedExpression,
                        calculateTypeForComplexReferences);
        context.accept(visitor);
        return visitor.getExpectedType();
    }

    private static class ExpectedTypeVisitor extends JavaElementVisitor{

        /**
         * @noinspection StaticCollection
         */
        private static final Set<IElementType> arithmeticOps =
                new HashSet<IElementType>(5);

        private static final Set<IElementType> comparisonOps =
                new HashSet<IElementType>(6);

        private static final Set<IElementType> booleanOps =
                new HashSet<IElementType>(5);

        private static final Set<IElementType> shiftOps =
                new HashSet<IElementType>(3);

        private static final Set<IElementType> operatorAssignmentOps =
                new HashSet<IElementType>(11);

        static {
            arithmeticOps.add(JavaTokenType.PLUS);
            arithmeticOps.add(JavaTokenType.MINUS);
            arithmeticOps.add(JavaTokenType.ASTERISK);
            arithmeticOps.add(JavaTokenType.DIV);
            arithmeticOps.add(JavaTokenType.PERC);

            comparisonOps.add(JavaTokenType.EQEQ);
            comparisonOps.add(JavaTokenType.NE);
            comparisonOps.add(JavaTokenType.LE);
            comparisonOps.add(JavaTokenType.LT);
            comparisonOps.add(JavaTokenType.GE);
            comparisonOps.add(JavaTokenType.GT);

            booleanOps.add(JavaTokenType.ANDAND);
            booleanOps.add(JavaTokenType.AND);
            booleanOps.add(JavaTokenType.XOR);
            booleanOps.add(JavaTokenType.OROR);
            booleanOps.add(JavaTokenType.OR);

            shiftOps.add(JavaTokenType.LTLT);
            shiftOps.add(JavaTokenType.GTGT);
            shiftOps.add(JavaTokenType.GTGTGT);

            operatorAssignmentOps.add(JavaTokenType.PLUSEQ);
            operatorAssignmentOps.add(JavaTokenType.MINUSEQ);
            operatorAssignmentOps.add(JavaTokenType.ASTERISKEQ);
            operatorAssignmentOps.add(JavaTokenType.DIVEQ);
            operatorAssignmentOps.add(JavaTokenType.ANDEQ);
            operatorAssignmentOps.add(JavaTokenType.OREQ);
            operatorAssignmentOps.add(JavaTokenType.XOREQ);
            operatorAssignmentOps.add(JavaTokenType.PERCEQ);
            operatorAssignmentOps.add(JavaTokenType.LTLTEQ);
            operatorAssignmentOps.add(JavaTokenType.GTGTEQ);
            operatorAssignmentOps.add(JavaTokenType.GTGTGTEQ);
        }

        private final PsiExpression wrappedExpression;
        private final boolean calculateTypeForComplexReferences;
        private PsiType expectedType = null;

        ExpectedTypeVisitor(PsiExpression wrappedExpression,
                            boolean calculateTypeForComplexReferences){
            this.wrappedExpression = wrappedExpression;
            this.calculateTypeForComplexReferences =
                    calculateTypeForComplexReferences;
        }

        public PsiType getExpectedType(){
            return expectedType;
        }

        @Override public void visitField(@NotNull PsiField field){
            final PsiExpression initializer = field.getInitializer();
            if(wrappedExpression.equals(initializer)){
                expectedType = field.getType();
            }
        }

        @Override public void visitVariable(@NotNull PsiVariable variable){
            expectedType = variable.getType();
        }

        @Override public void visitArrayInitializerExpression(
                PsiArrayInitializerExpression initializer){
            final PsiType type = initializer.getType();
            if(!(type instanceof PsiArrayType)){
                expectedType = null;
                return;
            }
            final PsiArrayType arrayType = (PsiArrayType) type;
            expectedType = arrayType.getComponentType();
        }

        @Override public void visitArrayAccessExpression(
                PsiArrayAccessExpression accessExpression){
            final PsiExpression indexExpression =
                    accessExpression.getIndexExpression();
            if(wrappedExpression.equals(indexExpression)){
                expectedType = PsiType.INT;
            }
        }

        @Override public void visitBinaryExpression(
                @NotNull PsiBinaryExpression binaryExpression) {
            final PsiJavaToken sign = binaryExpression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            final PsiType type = binaryExpression.getType();
            final PsiExpression rhs = binaryExpression.getROperand();
            if (rhs == null) {
                expectedType = null;
                return;
            }
            final PsiExpression lhs = binaryExpression.getLOperand();
            PsiType lhsType = lhs.getType();
            if (lhsType == null) {
                expectedType = null;
                return;
            }
            PsiType rhsType = rhs.getType();
            if (rhsType == null) {
                expectedType = null;
                return;
            }
            if (TypeUtils.isJavaLangString(type) ||
                    isArithmeticOperation(tokenType) ||
                    isBooleanOperation(tokenType)) {
                expectedType = type;
            } else if (isShiftOperation(tokenType)) {
                if (wrappedExpression.equals(lhs)) {
                    expectedType = unaryNumericPromotion(lhsType);
                } else {
                    expectedType = unaryNumericPromotion(rhsType);
                }
            } else if (ComparisonUtils.isComparisonOperation(tokenType)) {
                if (!ClassUtils.isPrimitive(lhsType)) {
                    lhsType = PsiPrimitiveType.getUnboxedType(lhsType);
                    if (lhsType == null) {
                        expectedType = null;
                        return;
                    }
                }
                if (!ClassUtils.isPrimitive(rhsType)) {
                    rhsType = PsiPrimitiveType.getUnboxedType(rhsType);
                    if (rhsType == null) {
                        expectedType = null;
                        return;
                    }
                }
                // JLS 5.6.2 Binary Numeric Promotion
                if (PsiType.DOUBLE.equals(lhsType) ||
                        PsiType.DOUBLE.equals(rhsType)) {
                    expectedType = PsiType.DOUBLE;
                } else if (PsiType.FLOAT.equals(lhsType) ||
                        PsiType.FLOAT.equals(rhsType)) {
                    expectedType = PsiType.FLOAT;
                } else if (PsiType.LONG.equals(lhsType) ||
                        PsiType.LONG.equals(rhsType)) {
                    expectedType = PsiType.LONG;
                } else {
                    expectedType = PsiType.INT;
                }
            } else {
                expectedType = null;
            }
        }

        /**
         * JLS 5.6.1 Unary Numeric Promotion
         */
        private static PsiType unaryNumericPromotion(PsiType type) {
            if (type == null) {
                return null;
            }
            if (type.equalsToText("java.lang.Byte") ||
                    type.equalsToText("java.lang.Short") ||
                    type.equalsToText("java.lang.Character") ||
                    type.equalsToText("java.lang.Integer") ||
                    type.equals(PsiType.BYTE) ||
                    type.equals(PsiType.SHORT) ||
                    type.equals(PsiType.CHAR)) {
                return PsiType.INT;
            } else if (type.equalsToText("java.lang.Long")) {
                return PsiType.LONG;
            } else if (type.equalsToText("java.lang.Float")) {
                return PsiType.FLOAT;
            } else if (type.equalsToText("java.lang.Double")) {
                return PsiType.DOUBLE;
            }
            return type;
        }

        @Override public void visitPrefixExpression(
                @NotNull PsiPrefixExpression expression){
            final PsiType type = expression.getType();
            if (type instanceof PsiPrimitiveType) {
                expectedType = type;
            } else {
                expectedType = PsiPrimitiveType.getUnboxedType(type);
            }
        }

        @Override public void visitPostfixExpression(
                @NotNull PsiPostfixExpression expression){
            final PsiType type = expression.getType();
            if (type instanceof PsiPrimitiveType) {
                expectedType = type;
            } else {
                expectedType = PsiPrimitiveType.getUnboxedType(type);
            }
        }

        @Override public void visitWhileStatement(
                @NotNull PsiWhileStatement whileStatement){
            expectedType = PsiType.BOOLEAN;
        }

        @Override public void visitForStatement(
                @NotNull PsiForStatement statement){
            expectedType = PsiType.BOOLEAN;
        }

        @Override public void visitIfStatement(
                @NotNull PsiIfStatement statement){
            expectedType = PsiType.BOOLEAN;
        }

        @Override public void visitDoWhileStatement(
                @NotNull PsiDoWhileStatement statement){
            expectedType = PsiType.BOOLEAN;
        }

        @Override public void visitSynchronizedStatement(
                @NotNull PsiSynchronizedStatement statement){
            final PsiManager manager = statement.getManager();
            final Project project = manager.getProject();
            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            expectedType = PsiType.getJavaLangObject(manager, scope);
        }

        @Override public void visitAssignmentExpression(
                @NotNull PsiAssignmentExpression assignment){
            final PsiExpression rExpression = assignment.getRExpression();
            final PsiJavaToken operationSign =
                    assignment.getOperationSign();
            final IElementType tokenType = operationSign.getTokenType();
            final PsiExpression lExpression =
                    assignment.getLExpression();
            final PsiType lType = lExpression.getType();
            if(rExpression != null &&
                    wrappedExpression.equals(rExpression)){
                if(lType == null){
                    expectedType = null;
                } else if(TypeUtils.isJavaLangString(lType)){
                    if(JavaTokenType.PLUSEQ.equals(tokenType)){
                        // e.g. String += any type
                        expectedType = rExpression.getType();
                    } else{
                        expectedType = lType;
                    }
                } else if (isOperatorAssignmentOperation(tokenType)){
                    if (lType instanceof PsiPrimitiveType){
                        expectedType = lType;
                    } else{
                        expectedType =
                                PsiPrimitiveType.getUnboxedType(lType);
                    }
                } else{
                    expectedType = lType;
                }
            } else{
                if (isOperatorAssignmentOperation(tokenType) &&
                        !(lType instanceof PsiPrimitiveType)){
                    expectedType = PsiPrimitiveType.getUnboxedType(lType);
                } else{
                    expectedType = lType;
                }
            }
        }

        @Override public void visitConditionalExpression(
                PsiConditionalExpression conditional){
            final PsiExpression condition = conditional.getCondition();
            if(condition.equals(wrappedExpression)){
                expectedType = PsiType.BOOLEAN;
            } else{
                expectedType = conditional.getType();
            }
        }

        @Override public void visitReturnStatement(
                @NotNull PsiReturnStatement returnStatement){
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(returnStatement,
                                                PsiMethod.class);
            if (method != null) {
                expectedType = method.getReturnType();
            }
        }

        @Override public void visitDeclarationStatement(
                PsiDeclarationStatement declaration){
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            for(PsiElement declaredElement : declaredElements){
                if(declaredElement instanceof PsiVariable){
                    final PsiVariable variable =
                            (PsiVariable) declaredElement;
                    final PsiExpression initializer =
                            variable.getInitializer();
                    if(wrappedExpression.equals(initializer)){
                        expectedType = variable.getType();
                        return;
                    }
                }
            }
        }

        @Override public void visitExpressionList(
                PsiExpressionList expressionList){
            final JavaResolveResult result = findCalledMethod(expressionList);
            final PsiMethod method = (PsiMethod) result.getElement();
            if(method == null){
                expectedType = null;
            } else{
                final int parameterPosition =
                        getParameterPosition(expressionList, wrappedExpression);
                expectedType = getTypeOfParameter(result, parameterPosition);
            }
        }

        @Override
        public void visitNewExpression(PsiNewExpression expression) {
            final PsiExpression[] arrayDimensions =
                    expression.getArrayDimensions();
            for (PsiExpression arrayDimension : arrayDimensions) {
                if (wrappedExpression.equals(arrayDimension)) {
                    expectedType = PsiType.INT;
                }
            }
        }

        @NotNull
        private static JavaResolveResult findCalledMethod(
                PsiExpressionList expressionList){
            final PsiElement parent = expressionList.getParent();
            if(parent instanceof PsiCallExpression){
                final PsiCallExpression call = (PsiCallExpression) parent;
                return call.resolveMethodGenerics();
            } else if (parent instanceof PsiAnonymousClass) {
                final PsiElement grandParent = parent.getParent();
                if (grandParent instanceof PsiCallExpression){
                    final PsiCallExpression callExpression =
                            (PsiCallExpression)grandParent;
                    return callExpression.resolveMethodGenerics();
                }
            }
            return JavaResolveResult.EMPTY;
        }

        @Override public void visitReferenceExpression(
                @NotNull PsiReferenceExpression referenceExpression){
            //Dave, do we need this at all? -> I think we do -- Bas
            if(calculateTypeForComplexReferences){
                final Project project = referenceExpression.getProject();
                final JavaResolveResult resolveResult =
                        referenceExpression.advancedResolve(false);
                final PsiElement element = resolveResult.getElement();
                PsiSubstitutor substitutor = resolveResult.getSubstitutor();
                final JavaPsiFacade psiFacade =
                        JavaPsiFacade.getInstance(project);
                if(element instanceof PsiField){
                    final PsiField field = (PsiField) element;
                    if (!isAccessibleFrom(field, referenceExpression)) {
                        return;
                    }
                    final PsiClass aClass = field.getContainingClass();
                    if (aClass == null) {
                        return;
                    }
                    final PsiElementFactory factory =
                            psiFacade.getElementFactory();
                    expectedType = factory.createType(aClass, substitutor);
                } else if(element instanceof PsiMethod){
                    final PsiElement parent = referenceExpression.getParent();
                    final PsiType returnType;
                    if (parent instanceof PsiMethodCallExpression) {
                        final PsiMethodCallExpression methodCallExpression =
                                (PsiMethodCallExpression) parent;
                        final PsiType type = methodCallExpression.getType();
                        if (!PsiType.VOID.equals(type)) {
                            returnType =
                                    findExpectedType(methodCallExpression, true);
                        } else {
                            returnType = null;
                        }
                    } else {
                        returnType = null;
                    }
                    final PsiMethod method = (PsiMethod) element;
                    final PsiMethod superMethod =
                            findDeepestVisibleSuperMethod(method, returnType,
                                    referenceExpression);
                    final PsiClass aClass;
                    if(superMethod != null){
                        aClass = superMethod.getContainingClass();
                        if (aClass == null) {
                            return;
                        }
                        substitutor =
                                TypeConversionUtil.getSuperClassSubstitutor(
                                        aClass, method.getContainingClass(),
                                        substitutor);
                    } else{
                        aClass = method.getContainingClass();
                        if (aClass == null) {
                            return;
                        }
                    }
                    final PsiElementFactory factory =
                            psiFacade.getElementFactory();
                    expectedType = factory.createType(aClass, substitutor);
                } else{
                    expectedType = null;
                }
            }
        }

        @Nullable
        private static PsiMethod findDeepestVisibleSuperMethod(
                PsiMethod method, PsiType returnType, PsiElement element){
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
            final PsiMethod[] superMethods =
                    aClass.findMethodsBySignature(method, true);
            PsiMethod topSuper = null;
            PsiClass topSuperContainingClass = null;
            for(PsiMethod superMethod : superMethods){
                final PsiClass superClass = superMethod.getContainingClass();
                if (superClass == null) {
                    continue;
                }
                if(aClass.equals(superClass)){
                    continue;
                }
                if(!isAccessibleFrom(superMethod, element)){
                    continue;
                }
                if (returnType != null) {
                    final PsiType superReturnType = superMethod.getReturnType();
                    if (superReturnType == null) {
                        continue;
                    }
                    if (!returnType.isAssignableFrom(superReturnType)) {
                        continue;
                    }
                }
                if(topSuper != null &&
                        superClass.isInheritor(topSuperContainingClass, true)){
                    continue;
                }
                topSuper = superMethod;
                topSuperContainingClass = superClass;
            }
            return topSuper;
        }

        private static boolean isAccessibleFrom(PsiMember member,
                                             PsiElement referencingLocation){
            if(member.hasModifierProperty(PsiModifier.PUBLIC)){
                return true;
            }
            final PsiClass containingClass = member.getContainingClass();
            if (containingClass == null) {
                return false;
            }
            final PsiClass referencingClass =
                    ClassUtils.getContainingClass(referencingLocation);
            if (referencingClass == null){
                return false;
            }
            if(referencingClass.equals(containingClass)){
                return true;
            }
            if(member.hasModifierProperty(PsiModifier.PRIVATE)){
                return false;
            }
            return ClassUtils.inSamePackage(containingClass,
                    referencingLocation);
        }

        private static boolean isArithmeticOperation(
                @NotNull IElementType sign){
            return arithmeticOps.contains(sign);
        }

        private static boolean isBooleanOperation(
                @NotNull IElementType sign){
            return booleanOps.contains(sign);
        }

        private static boolean isShiftOperation(
                @NotNull IElementType sign){
            return shiftOps.contains(sign);
        }

        private static boolean isOperatorAssignmentOperation(
                @NotNull IElementType sign){
            return operatorAssignmentOps.contains(sign);
        }

        private static int getParameterPosition(
                @NotNull PsiExpressionList expressionList,
                PsiExpression expression) {
            final PsiExpression[] expressions = expressionList.getExpressions();
            for(int i = 0; i < expressions.length; i++){
                if(expressions[i].equals(expression)){
                    return i;
                }
            }
            return -1;
        }

        @Nullable
        private static PsiType getTypeOfParameter(
                @NotNull JavaResolveResult result, int parameterPosition) {
            final PsiMethod method = (PsiMethod) result.getElement();
            if (method == null){
                return null;
            }
            final PsiSubstitutor substitutor = result.getSubstitutor();
            final PsiParameterList parameterList = method.getParameterList();
            if(parameterPosition < 0){
                return null;
            }
            final int parametersCount = parameterList.getParametersCount();
            final PsiParameter[] parameters;
            if (parameterPosition >= parametersCount) {
                final int lastParameterPosition = parametersCount - 1;
                if (lastParameterPosition < 0) {
                    return null;
                }
                parameters = parameterList.getParameters();
                final PsiParameter lastParameter =
                        parameters[lastParameterPosition];
                if (lastParameter.isVarArgs()) {
                    final PsiArrayType arrayType =
                            (PsiArrayType) lastParameter.getType();
                    return substitutor.substitute(arrayType.getComponentType());
                }
                return null;
            }
            parameters = parameterList.getParameters();
            final PsiParameter parameter = parameters[parameterPosition];
            final PsiType parameterType = parameter.getType();
            if(parameter.isVarArgs()){
                final PsiArrayType arrayType =
                        (PsiArrayType)parameterType;
                return substitutor.substitute(arrayType.getComponentType());
            }
            final PsiType type = substitutor.substitute(parameterType);
            final TypeStringCreator typeStringCreator = new TypeStringCreator();
            type.accept(typeStringCreator);
            if (typeStringCreator.isModified()) {
                final PsiManager manager = method.getManager();
                final Project project = manager.getProject();
                final PsiElementFactory factory =
                        JavaPsiFacade.getInstance(project).getElementFactory();
                try {
                    final String typeString = typeStringCreator.getTypeString();
                    return factory.createTypeFromText(typeString, method);
                } catch (IncorrectOperationException e) {
                    throw new AssertionError("incorrect type string generated from " +
                            type + ": " + e.getMessage());
                }
            }
            return type;
        }

        /**
         * Creates a new type string without any wildcards with final
         * extends bounds from the visited type.
         */
        private static class TypeStringCreator extends PsiTypeVisitor<Object> {

            private final StringBuilder typeString = new StringBuilder();
            private boolean modified = false;

            @Override
            public Object visitType(PsiType type) {
                typeString.append(type.getCanonicalText());
                return super.visitType(type);
            }

            @Override
            public Object visitWildcardType(PsiWildcardType wildcardType) {
                if (wildcardType.isExtends()) {
                    final PsiType extendsBound = wildcardType.getExtendsBound();
                    if (extendsBound instanceof PsiClassType) {
                        final PsiClassType classType =
                                (PsiClassType) extendsBound;
                        final PsiClass aClass = classType.resolve();
                        if (aClass != null &&
                                aClass.hasModifierProperty(PsiModifier.FINAL)) {
                            modified = true;
                            return super.visitClassType(classType);
                        }

                    }
                }
                return super.visitWildcardType(wildcardType);
            }

            @Override
            public Object visitClassType(PsiClassType classType) {
                final PsiClassType rawType = classType.rawType();
                typeString.append(rawType.getCanonicalText());
                final PsiType[] parameterTypes = classType.getParameters();
                if (parameterTypes.length > 0) {
                    typeString.append('<');
                    final PsiType parameterType1 = parameterTypes[0];
                    // IDEADEV-25549 says this can be null
                    if (parameterType1 != null) {
                        parameterType1.accept(this);
                    }
                    for (int i = 1; i < parameterTypes.length; i++) {
                        typeString.append(',');
                        final PsiType parameterType = parameterTypes[i];
                        // IDEADEV-25549 again
                        if (parameterType != null) {
                            parameterType.accept(this);
                        }
                    }
                    typeString.append('>');
                }
                return null;
            }

            public String getTypeString() {
                return typeString.toString();
            }

            public boolean isModified() {
                return modified;
            }
        }
    }
}
