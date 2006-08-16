/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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

import com.intellij.psi.*;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.Query;
import com.intellij.openapi.project.Project;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MethodUtils{

    private MethodUtils(){
        super();
    }

    public static boolean isCompareTo(PsiMethod method){
        return methodMatches(method, null, PsiType.INT,
                HardcodedMethodConstants.COMPARE_TO, PsiType.NULL);
    }

    public static boolean isHashCode(PsiMethod method){
        return methodMatches(method, null, PsiType.INT,
                HardcodedMethodConstants.HASH_CODE);
    }

    public static boolean isEquals(PsiMethod method){
        final PsiManager manager = method.getManager();
        final Project project = method.getProject();
        final PsiClassType objectType = PsiType.getJavaLangObject(
                manager, GlobalSearchScope.allScope(project));
        return methodMatches(method, null, PsiType.BOOLEAN,
                HardcodedMethodConstants.EQUALS, objectType);
    }

    public static boolean methodMatches(@NotNull PsiMethod method,
                                        @Nullable String containingClassName,
                                        @NotNull PsiType returnType,
                                        @NotNull String methodName,
                                        @NotNull PsiType... parameterTypes) {
        final String name = method.getName();
        if (!methodName.equals(name)) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != parameterTypes.length) {
            return false;
        }
        for (int i = 0; i < parameters.length; i++) {
            final PsiParameter parameter = parameters[i];
            final PsiType type = parameter.getType();
            final PsiType parameterType = parameterTypes[i];
            if (parameterType != PsiType.NULL &&
                    !EquivalenceChecker.typesAreEquivalent(type, parameterType)) {
                return false;
            }
        }
        if (returnType != PsiType.NULL) {
        final PsiType methodReturnType = method.getReturnType();
            if (!EquivalenceChecker.typesAreEquivalent(returnType,
                    methodReturnType)) {
                return false;
            }
        }
        if (containingClassName == null) {
            return true;
        }
        final PsiClass containingClass = method.getContainingClass();
        return ClassUtils.isSubclass(containingClass, containingClassName);
    }

    public static boolean isOverridden(PsiMethod method){
        final Query<PsiMethod> overridingMethodQuery =
                OverridingMethodsSearch.search(method);
        final PsiMethod result = overridingMethodQuery.findFirst();
        return result != null;
    }

    public static boolean isOverriddenInHierarchy(PsiMethod method,
                                                  PsiClass baseClass) {
        final Query<PsiMethod> search = OverridingMethodsSearch.search(method);
        for (PsiMethod overridingMethod : search) {
            final PsiClass aClass = overridingMethod.getContainingClass();
            if (InheritanceUtil.isCorrectDescendant(aClass, baseClass, true)) {
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
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != 0){
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
        final PsiParameter[] parameters = parameterList.getParameters();
        if (parameters.length != 1){
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