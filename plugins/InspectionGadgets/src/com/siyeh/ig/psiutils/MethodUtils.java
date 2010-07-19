/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MethodUtils{

    private MethodUtils(){
    }

    public static boolean isCompareTo(@Nullable PsiMethod method){
        if (method == null) {
            return false;
        }
        return methodMatches(method, null, PsiType.INT,
                HardcodedMethodConstants.COMPARE_TO, PsiType.NULL);
    }

    public static boolean isHashCode(@Nullable PsiMethod method){
        if (method == null) {
            return false;
        }
        return methodMatches(method, null, PsiType.INT,
                HardcodedMethodConstants.HASH_CODE);
    }

    public static boolean isToString(@Nullable PsiMethod method){
        if (method == null) {
            return false;
        }
        final PsiManager manager = method.getManager();
        final GlobalSearchScope scope = method.getResolveScope();
        final PsiClassType stringType =
                PsiType.getJavaLangString(manager, scope);
        return methodMatches(method, null, stringType,
                HardcodedMethodConstants.TO_STRING);
    }

    public static boolean isEquals(@Nullable PsiMethod method){
        if (method == null) {
            return false;
        }
        final PsiManager manager = method.getManager();
        final Project project = method.getProject();
        final PsiClassType objectType = PsiType.getJavaLangObject(
                manager, GlobalSearchScope.allScope(project));
        return methodMatches(method, null, PsiType.BOOLEAN,
                HardcodedMethodConstants.EQUALS, objectType);
    }

    /**
     * @param method  the method to compare to.
     * @param containingClassName  the name of the class which contiains the
     * method.
     * @param returnType  the return type, specify null if any type matches
     * @param methodNamePattern  the name the method should have
     * @param parameterTypes  the type of the parameters of the method, specify
     *  null if any number and type of parameters match or an empty array
     * to match zero parameters.
     * @return true, if the specified method matches the specified constraints,
     *  false otherwise
     */
    public static boolean methodMatches(
            @NotNull PsiMethod method,
            @NonNls @Nullable String containingClassName,
            @Nullable PsiType returnType,
            @Nullable Pattern methodNamePattern,
            @Nullable PsiType... parameterTypes) {
        if (methodNamePattern != null) {
            final String name = method.getName();
            final Matcher matcher = methodNamePattern.matcher(name);
            if (!matcher.matches()) {
                return false;
            }
        }
        if (parameterTypes != null) {
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != parameterTypes.length) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                final PsiParameter parameter = parameters[i];
                final PsiType type = parameter.getType();
                final PsiType parameterType = parameterTypes[i];
                if (PsiType.NULL.equals(parameterType)) {
                    continue;
                }
                if (parameterType != null &&
                        !EquivalenceChecker.typesAreEquivalent(type,
                                parameterType)) {
                    return false;
                }
            }
        }
        if (returnType != null) {
            final PsiType methodReturnType = method.getReturnType();
            if (!EquivalenceChecker.typesAreEquivalent(returnType,
                    methodReturnType)) {
                return false;
            }
        }
        if (containingClassName != null) {
            final PsiClass containingClass = method.getContainingClass();
            return ClassUtils.isSubclass(containingClass, containingClassName);
        }
        return true;
    }

    /**
     * @param method  the method to compare to.
     * @param containingClassName  the name of the class which contiains the
     * method.
     * @param returnType  the return type, specify null if any type matches
     * @param methodName  the name the method should have
     * @param parameterTypes  the type of the parameters of the method, specify
     *  null if any number and type of parameters match or an empty array
     * to match zero parameters.
     * @return true, if the specified method matches the specified constraints,
     *  false otherwise
     */
    public static boolean methodMatches(
            @NotNull PsiMethod method,
            @NonNls @Nullable String containingClassName,
            @Nullable PsiType returnType,
            @NonNls @Nullable String methodName,
            @Nullable PsiType... parameterTypes) {
        final String name = method.getName();
        if (methodName != null && !methodName.equals(name)) {
            return false;
        }
        if (parameterTypes != null) {
            final PsiParameterList parameterList = method.getParameterList();
            if (parameterList.getParametersCount() != parameterTypes.length) {
                return false;
            }
            final PsiParameter[] parameters = parameterList.getParameters();
            for (int i = 0; i < parameters.length; i++) {
                final PsiParameter parameter = parameters[i];
                final PsiType type = parameter.getType();
                final PsiType parameterType = parameterTypes[i];
                if (PsiType.NULL.equals(parameterType)) {
                    continue;
                }
                if (parameterType != null &&
                        !EquivalenceChecker.typesAreEquivalent(type,
                                parameterType)) {
                    return false;
                }
            }
        }
        if (returnType != null) {
            final PsiType methodReturnType = method.getReturnType();
            if (!EquivalenceChecker.typesAreEquivalent(returnType,
                    methodReturnType)) {
                return false;
            }
        }
        if (containingClassName != null) {
            final PsiClass containingClass = method.getContainingClass();
            return ClassUtils.isSubclass(containingClass, containingClassName);
        }
        return true;
    }

    public static boolean simpleMethodMatches(
            @NotNull PsiMethod method,
            @NonNls @Nullable String containingClassName,
            @NonNls @Nullable String returnTypeString,
            @NonNls @Nullable String methodName,
            @NonNls @Nullable String... parameterTypeStrings) {
        final Project project = method.getProject();
        final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
        final PsiElementFactory factory = psiFacade.getElementFactory();
        try {
            if (parameterTypeStrings != null) {
                final PsiType[] parameterTypes =
                        new PsiType[parameterTypeStrings.length];
                for (int i = 0; i < parameterTypeStrings.length; i++) {
                    final String parameterTypeString = parameterTypeStrings[i];
                    parameterTypes[i] = factory.createTypeFromText(
                            parameterTypeString, method);
                }
                if (returnTypeString != null) {
                    final PsiType returnType =
                            factory.createTypeFromText(returnTypeString,
                                    method);
                    return methodMatches(method, containingClassName, returnType,
                            methodName, parameterTypes);
                } else {
                    return methodMatches(method, containingClassName, null,
                            methodName, parameterTypes);
                }
            } else if (returnTypeString != null) {
                final PsiType returnType =
                        factory.createTypeFromText(returnTypeString, method);
                return methodMatches(method, containingClassName, returnType,
                        methodName);
            } else {
                return methodMatches(method, containingClassName, null,
                        methodName);
            }
        } catch (IncorrectOperationException e) {
            throw new RuntimeException(e);
        }
    }


    public static boolean isOverridden(PsiMethod method){
        final Query<PsiMethod> overridingMethodQuery =
                OverridingMethodsSearch.search(method);
        final PsiMethod result = overridingMethodQuery.findFirst();
        return result != null;
    }

    public static boolean isOverriddenInHierarchy(PsiMethod method,
                                                  PsiClass baseClass) {
        // previous implementation:
        // final Query<PsiMethod> search = OverridingMethodsSearch.search(method);
        //for (PsiMethod overridingMethod : search) {
        //    final PsiClass aClass = overridingMethod.getContainingClass();
        //    if (InheritanceUtil.isCorrectDescendant(aClass, baseClass, true)) {
        //        return true;
        //    }
        //}
        // was extremely slow and used an enormous amount of memory for clone()
        final Query<PsiClass> search =
                ClassInheritorsSearch.search(baseClass, baseClass.getUseScope(),
                        true, true, true);
        for (PsiClass inheritor : search) {
            final PsiMethod overridingMethod =
                    inheritor.findMethodBySignature(method, false);
            if (overridingMethod != null) {
                return true;
            }
        }
        return false;
    }

    public static boolean isEmpty(PsiMethod method){
        final PsiCodeBlock body = method.getBody();
        if (body == null){
            return true;
        }
        final PsiStatement[] statements = body.getStatements();
        return statements.length == 0;
    }

    @Nullable
    public static PsiField getFieldOfGetter(PsiMethod method) {
        if (method == null) {
            return null;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 0){
            return null;
        }
        @NonNls final String name = method.getName();
        if (!name.startsWith("get") && !name.startsWith("is")) {
            return null;
        }
        if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
            return null;
        }
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return null;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements.length != 1){
            return null;
        }
        final PsiStatement statement = statements[0];
        if(!(statement instanceof PsiReturnStatement)){
            return null;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) statement;
        final PsiExpression value = returnStatement.getReturnValue();
        if(value == null){
            return null;
        }
        if(!(value instanceof PsiReferenceExpression)){
            return null;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) value;
        final PsiExpression qualifier = reference.getQualifierExpression();
        if(qualifier != null && !(qualifier instanceof PsiThisExpression)
           && !(qualifier instanceof PsiSuperExpression)){
            return null;
        }
        final PsiElement referent = reference.resolve();
        if(referent == null){
            return null;
        }
        if(!(referent instanceof PsiField)){
            return null;
        }
        final PsiField field = (PsiField) referent;
        final PsiType fieldType = field.getType();
        final PsiType returnType = method.getReturnType();
        if(returnType == null){
            return null;
        }
        if(!fieldType.equalsToText(returnType.getCanonicalText())){
            return null;
        }
        final PsiClass fieldContainingClass = field.getContainingClass();
        final PsiClass methodContainingClass = method.getContainingClass();
        if (InheritanceUtil.isCorrectDescendant(methodContainingClass,
                fieldContainingClass, true)) {
            return field;
        } else {
            return null;
        }
    }

    public static boolean isSimpleGetter(PsiMethod method){
        return getFieldOfGetter(method) != null;
    }

    @Nullable
    public static PsiField getFieldOfSetter(PsiMethod method) {
        if (method == null) {
            return null;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() != 1){
            return null;
        }
        @NonNls final String name = method.getName();
        if (!name.startsWith("set")) {
            return null;
        }
        if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)){
            return null;
        }
        final PsiCodeBlock body = method.getBody();
        if(body == null){
            return null;
        }
        final PsiStatement[] statements = body.getStatements();
        if(statements.length != 1){
            return null;
        }
        final PsiStatement statement = statements[0];
        if(!(statement instanceof PsiExpressionStatement)){
            return null;
        }
        final PsiExpressionStatement possibleAssignmentStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression possibleAssignment =
                possibleAssignmentStatement.getExpression();
        if(!(possibleAssignment instanceof PsiAssignmentExpression)){
            return null;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) possibleAssignment;
        final PsiJavaToken sign = assignment.getOperationSign();
        if(!JavaTokenType.EQ.equals(sign.getTokenType())){
            return null;
        }
        final PsiExpression lhs = assignment.getLExpression();
        if(!(lhs instanceof PsiReferenceExpression)){
            return null;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) lhs;
        final PsiExpression qualifier = reference.getQualifierExpression();
        if(qualifier != null && !(qualifier instanceof PsiThisExpression) &&
           !(qualifier instanceof PsiSuperExpression)){
            return null;
        }
        final PsiElement referent = reference.resolve();
        if(referent == null){
            return null;
        }
        if(!(referent instanceof PsiField)){
            return null;
        }
        final PsiField field = (PsiField) referent;
        final PsiClass fieldContainingClass = field.getContainingClass();
        final PsiClass methodContainingClass = method.getContainingClass();
        if(!InheritanceUtil.isCorrectDescendant(methodContainingClass,
                fieldContainingClass, true)){
            return null;
        }
        final PsiExpression rhs = assignment.getRExpression();
        if(!(rhs instanceof PsiReferenceExpression)){
            return null;
        }
        final PsiReferenceExpression rReference = (PsiReferenceExpression) rhs;
        final PsiExpression rQualifier = rReference.getQualifierExpression();
        if(rQualifier != null){
            return null;
        }
        final PsiElement rReferent = rReference.resolve();
        if(rReferent == null){
            return null;
        }
        if(!(rReferent instanceof PsiParameter)){
            return null;
        }
        final PsiType fieldType = field.getType();
        final PsiType parameterType = ((PsiVariable) rReferent).getType();
        if (fieldType.equalsToText(parameterType.getCanonicalText())) {
            return field;
        } else {
            return null;
        }
    }

    public static boolean isSimpleSetter(PsiMethod method){
        return getFieldOfSetter(method) != null;
    }
}
