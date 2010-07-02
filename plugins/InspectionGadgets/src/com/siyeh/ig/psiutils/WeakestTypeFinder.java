/*
 * Copyright 2008-2009 Bas Leijdekkers
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
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
public class WeakestTypeFinder {

    private WeakestTypeFinder() {
    }

    @NotNull
    public static Collection<PsiClass> calculateWeakestClassesNecessary(
            @NotNull PsiElement variableOrMethod,
            boolean useRighthandTypeAsWeakestTypeInAssignments,
            boolean useParameterizedTypeForCollectionMethods) {
        final PsiType variableOrMethodType;
        if (variableOrMethod instanceof PsiVariable) {
            final PsiVariable variable = (PsiVariable) variableOrMethod;
            variableOrMethodType = variable.getType();
        } else if (variableOrMethod instanceof PsiMethod) {
            final PsiMethod method = (PsiMethod) variableOrMethod;
            variableOrMethodType = method.getReturnType();
            if (PsiType.VOID.equals(variableOrMethodType)) {
                return Collections.emptyList();
            }
        } else {
            throw new IllegalArgumentException(
                    "PsiMethod or PsiVariable expected: " +
                            variableOrMethod);
        }
        if (!(variableOrMethodType instanceof PsiClassType)) {
            return Collections.emptyList();
        }
        final PsiClassType variableOrMethodClassType =
                (PsiClassType) variableOrMethodType;
        final PsiClass variableOrMethodClass =
                variableOrMethodClassType.resolve();
        if (variableOrMethodClass == null) {
            return Collections.emptyList();
        }
        final PsiManager manager = variableOrMethod.getManager();
        final GlobalSearchScope scope = variableOrMethod.getResolveScope();
        final Project project = manager.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass javaLangObjectClass =
                facade.findClass("java.lang.Object", scope);
        if (javaLangObjectClass == null ||
                variableOrMethodClass.equals(javaLangObjectClass)) {
            return Collections.emptyList();
        }
        Set<PsiClass> weakestTypeClasses = new HashSet();
        weakestTypeClasses.add(javaLangObjectClass);
        final Query<PsiReference> query =
                ReferencesSearch.search(variableOrMethod);
        boolean hasUsages = false;
        for (PsiReference reference : query) {
            if (reference == null) {
                continue;
            }
            hasUsages = true;
            PsiElement referenceElement = reference.getElement();
            PsiElement referenceParent = referenceElement.getParent();
            if (referenceParent instanceof PsiMethodCallExpression) {
                referenceElement = referenceParent;
                referenceParent = referenceElement.getParent();
            }
            final PsiElement referenceGrandParent =
                    referenceParent.getParent();
            if (referenceParent instanceof PsiExpressionList) {
                if (!(referenceGrandParent instanceof
                        PsiMethodCallExpression)) {
                    return Collections.emptyList();
                }
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) referenceGrandParent;
                if (!findWeakestType(referenceElement, methodCallExpression,
                        useParameterizedTypeForCollectionMethods,
                        weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceGrandParent
                    instanceof PsiMethodCallExpression) {
                final PsiMethodCallExpression methodCallExpression =
                        (PsiMethodCallExpression) referenceGrandParent;
                if (!findWeakestType(methodCallExpression, weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiAssignmentExpression) {
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression) referenceParent;
                if (!findWeakestType(referenceElement, assignmentExpression,
                        useRighthandTypeAsWeakestTypeInAssignments,
                        weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable)referenceParent;
                final PsiType type = variable.getType();
                if (!checkType(type, weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiForeachStatement) {
                final PsiForeachStatement foreachStatement =
                        (PsiForeachStatement)referenceParent;
                if (foreachStatement.getIteratedValue() != referenceElement) {
                    return Collections.emptyList();
                }
                final PsiClass javaLangIterableClass =
                        facade.findClass("java.lang.Iterable", scope);
                if (javaLangIterableClass == null) {
                    return Collections.emptyList();
                }
                checkClass(javaLangIterableClass, weakestTypeClasses);
            } else if (referenceParent instanceof PsiReturnStatement) {
                final PsiMethod containingMethod =
                        PsiTreeUtil.getParentOfType(referenceParent,
                                PsiMethod.class);
                if (containingMethod == null) {
                    return Collections.emptyList();
                }
                final PsiType type = containingMethod.getReturnType();
                if (!checkType(type, weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiReferenceExpression) {
                // field access, method call is handled above.
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)referenceParent;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiField)) {
                    return Collections.emptyList();
                }
                final PsiField field = (PsiField)target;
                final PsiClass containingClass = field.getContainingClass();
                checkClass(containingClass, weakestTypeClasses);
            } else if (referenceParent instanceof PsiArrayInitializerExpression) {
                final PsiArrayInitializerExpression arrayInitializerExpression =
                        (PsiArrayInitializerExpression)referenceParent;
                if (!findWeakestType(arrayInitializerExpression,
                        weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiThrowStatement) {
                final PsiThrowStatement throwStatement =
                        (PsiThrowStatement)referenceParent;
                if (!findWeakestType(throwStatement, variableOrMethodClass,
                        weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiConditionalExpression) {
                final PsiConditionalExpression conditionalExpression =
                        (PsiConditionalExpression)referenceParent;
                final PsiExpression condition =
                        conditionalExpression.getCondition();
                if (referenceElement.equals(condition)) {
                    return Collections.emptyList();
                }
                final PsiType type = ExpectedTypeUtils.findExpectedType(
                        conditionalExpression, true);
                if (!checkType(type, weakestTypeClasses)) {
                    return Collections.emptyList();
                }
            } else if (referenceParent instanceof PsiBinaryExpression) {
                // strings only
                final PsiBinaryExpression binaryExpression =
                        (PsiBinaryExpression)referenceParent;
                final PsiType type = binaryExpression.getType();
                if (variableOrMethodType.equals(type)) {
                    if (!checkType(type, weakestTypeClasses)) {
                        return Collections.emptyList();
                    }
                }
            } else if (referenceParent instanceof PsiSwitchStatement) {
                // only enums and primitives can be a switch expression
                return Collections.emptyList();
            } else if (referenceParent instanceof PsiPrefixExpression) {
                // only primitives and boxed types are the target of a prefix
                // expression
                return Collections.emptyList();
            } else if (referenceParent instanceof PsiPostfixExpression) {
                // only primitives and boxed types are the target of a postfix
                // expression
                return Collections.emptyList();
            } else if (referenceParent instanceof PsiIfStatement) {
                // only booleans and boxed Booleans are the condition of an if
                // statement
                return Collections.emptyList();
            } else if (referenceParent instanceof PsiForStatement) {
                // only booleans and boxed Booleans are the condition of an
                // for statement
                return Collections.emptyList();
            } else if (referenceParent instanceof PsiNewExpression) {
                final PsiNewExpression newExpression =
                        (PsiNewExpression)referenceParent;
                final PsiExpression qualifier = newExpression.getQualifier();
                if (qualifier != null) {
                    final PsiType type = newExpression.getType();
                    if (!(type instanceof PsiClassType)) {
                        return Collections.emptyList();
                    }
                    final PsiClassType classType = (PsiClassType)type;
                    final PsiClass innerClass = classType.resolve();
                    if (innerClass == null) {
                        return Collections.emptyList();
                    }
                    final PsiClass outerClass =
                            innerClass.getContainingClass();
                    if (outerClass != null) {
                        checkClass(outerClass, weakestTypeClasses);
                    }
                }
            }
            if (weakestTypeClasses.contains(variableOrMethodClass) ||
                    weakestTypeClasses.isEmpty()) {
                return Collections.emptyList();
            }
        }
        if (!hasUsages) {
            return Collections.emptyList();
        }
        weakestTypeClasses =
                filterAccessibleClasses(weakestTypeClasses, variableOrMethod);
        return weakestTypeClasses;
    }

    private static boolean findWeakestType(
            PsiElement referenceElement,
            PsiMethodCallExpression methodCallExpression,
            boolean useParameterizedTypeForCollectionMethods,
            Set<PsiClass> weakestTypeClasses) {
        if (!(referenceElement instanceof PsiExpression)) {
            return false;
        }
        final JavaResolveResult resolveResult =
                methodCallExpression.resolveMethodGenerics();
        final PsiMethod method = (PsiMethod) resolveResult.getElement();
        if (method == null) {
            return false;
        }
        final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
        final PsiExpressionList expressionList =
                methodCallExpression.getArgumentList();
        final PsiExpression[] expressions = expressionList.getExpressions();
        final int index =
                findElementIndexInExpressionList(referenceElement,
                        expressions);
        if (index < 0) {
            return false;
        }
        final PsiParameterList parameterList = method.getParameterList();
        if (parameterList.getParametersCount() == 0) {
            return false;
        }
        final PsiParameter[] parameters = parameterList.getParameters();
        final PsiParameter parameter;
        final PsiType type;
        if (index < parameters.length) {
            parameter = parameters[index];
            type = parameter.getType();
        } else {
            parameter = parameters[parameters.length - 1];
            type = parameter.getType();
            if (!(type instanceof PsiEllipsisType)) {
                return false;
            }
        }
        if (!useParameterizedTypeForCollectionMethods) {
            return checkType(type, substitutor, weakestTypeClasses);
        }
        final String methodName = method.getName();
        if (HardcodedMethodConstants.REMOVE.equals(methodName) ||
                HardcodedMethodConstants.GET.equals(methodName) ||
                "containsKey".equals(methodName) ||
                "containsValue".equals(methodName) ||
                "contains".equals(methodName) ||
                HardcodedMethodConstants.INDEX_OF.equals(methodName) ||
                HardcodedMethodConstants.LAST_INDEX_OF.equals(methodName)) {
            final PsiClass containingClass = method.getContainingClass();
            if (ClassUtils.isSubclass(containingClass,
                    "java.util.Map") || ClassUtils.isSubclass(containingClass,
                    "java.util.Collection")) {
                final PsiReferenceExpression methodExpression =
                        methodCallExpression.getMethodExpression();
                final PsiExpression qualifier =
                        methodExpression.getQualifierExpression();
                if (qualifier != null) {
                    final PsiType qualifierType = qualifier.getType();
                    if (qualifierType instanceof PsiClassType) {
                        final PsiClassType classType = (PsiClassType) qualifierType;
                        final PsiType[] parameterTypes =
                                classType.getParameters();
                        if (parameterTypes.length > 0) {
                            final PsiType parameterType =
                                    parameterTypes[0];
                            final PsiExpression expression = expressions[index];
                            final PsiType expressionType = expression.getType();
                            if (expressionType == null ||
                                    parameterType == null ||
                                    !parameterType.isAssignableFrom(
                                            expressionType)) {
                                return false;
                            }
                            return checkType(parameterType, substitutor,
                                    weakestTypeClasses);
                        }
                    }
                }
            }
        }
        return checkType(type, substitutor, weakestTypeClasses);
    }

    private static boolean checkType(
            @Nullable PsiType type, @NotNull PsiSubstitutor substitutor,
            @NotNull Collection<PsiClass> weakestTypeClasses) {
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return false;
        }
        if (aClass instanceof PsiTypeParameter) {
            final PsiType substitution = substitutor.substitute(
                    (PsiTypeParameter) aClass);
            return checkType(substitution, weakestTypeClasses);
        }
        checkClass(aClass, weakestTypeClasses);
        return true;
    }

    private static boolean findWeakestType(
            PsiMethodCallExpression methodCallExpression,
            Set<PsiClass> weakestTypeClasses) {
        final PsiReferenceExpression methodExpression =
                methodCallExpression.getMethodExpression();
        final PsiElement target = methodExpression.resolve();
        if (!(target instanceof PsiMethod)) {
            return false;
        }
        final PsiMethod method = (PsiMethod) target;
        final PsiReferenceList throwsList = method.getThrowsList();
        final PsiClassType[] classTypes =
                throwsList.getReferencedTypes();
        final Collection<PsiClassType> thrownTypes =
                new HashSet(Arrays.asList(classTypes));
        final PsiMethod[] superMethods =
                method.findDeepestSuperMethods();
        boolean checked = false;
        if (superMethods.length > 0) {
            final PsiType expectedType =
                    ExpectedTypeUtils.findExpectedType(
                            methodCallExpression, false);
            for (PsiMethod superMethod : superMethods) {
                final PsiType returnType = superMethod.getReturnType();
                if (expectedType != null && returnType != null &&
                        !expectedType.isAssignableFrom(returnType)) {
                    continue;
                }
                if (throwsIncompatibleException(superMethod,
                        thrownTypes)) {
                    continue;
                }
                if (!isAccessibleFrom(superMethod, methodCallExpression)) {
                    continue;
                }
                final PsiClass containingClass =
                        superMethod.getContainingClass();
                checkClass(containingClass, weakestTypeClasses);
                checked = true;
            }
        }
        if (!checked) {
            final PsiType returnType = method.getReturnType();
            if (returnType instanceof PsiClassType) {
                final PsiClassType classType = (PsiClassType)returnType;
                final PsiClass aClass = classType.resolve();
                if (aClass instanceof PsiTypeParameter) {
                    return false;
                }
            }
            final PsiClass containingClass =
                    method.getContainingClass();
            checkClass(containingClass, weakestTypeClasses);
        }
        return true;
    }

    private static boolean findWeakestType(
            PsiElement referenceElement, PsiAssignmentExpression assignmentExpression,
            boolean useRighthandTypeAsWeakestTypeInAssignments,
            Set<PsiClass> weakestTypeClasses) {
        final IElementType tokenType =
                assignmentExpression.getOperationTokenType();
        if (JavaTokenType.EQ != tokenType) {
            return false;
        }
        final PsiExpression lhs =
                assignmentExpression.getLExpression();
        final PsiExpression rhs =
                assignmentExpression.getRExpression();
        final PsiType lhsType = lhs.getType();
        if (referenceElement.equals(rhs)) {
            if (!checkType(lhsType, weakestTypeClasses)) {
                return false;
            }
        } else if (useRighthandTypeAsWeakestTypeInAssignments) {
            if (rhs == null) {
                return false;
            }
            if (!(rhs instanceof PsiNewExpression) ||
                    !(rhs instanceof PsiTypeCastExpression)) {
                final PsiType rhsType = rhs.getType();
                if (lhsType == null || lhsType.equals(rhsType)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean findWeakestType(
            PsiArrayInitializerExpression arrayInitializerExpression,
            Set<PsiClass> weakestTypeClasses) {
        final PsiType type = arrayInitializerExpression.getType();
        if (!(type instanceof PsiArrayType)) {
            return false;
        }
        final PsiArrayType arrayType = (PsiArrayType)type;
        final PsiType componentType = arrayType.getComponentType();
        return checkType(componentType, weakestTypeClasses);
    }

    private static boolean findWeakestType(PsiThrowStatement throwStatement,
                                           PsiClass variableOrMethodClass,
                                           Set<PsiClass> weakestTypeClasses) {
        final PsiManager manager = throwStatement.getManager();
        final PsiClassType runtimeExceptionType =
                PsiType.getJavaLangRuntimeException(manager,
                        throwStatement.getResolveScope());
        final PsiClass runtimeExceptionClass = runtimeExceptionType.resolve();
        if (runtimeExceptionClass != null &&
                com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf(variableOrMethodClass,
                        runtimeExceptionClass, true)) {
            if (!checkType(runtimeExceptionType, weakestTypeClasses)) {
                return false;
            }
        } else {
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(throwStatement,
                            PsiMethod.class);
            if (method == null) {
                return false;
            }
            final PsiReferenceList throwsList = method.getThrowsList();
            final PsiClassType[] referencedTypes =
                    throwsList.getReferencedTypes();
            boolean checked = false;
            for (PsiClassType referencedType : referencedTypes) {
                final PsiClass throwableClass = referencedType.resolve();
                if (throwableClass == null ||
                        !InheritanceUtil.isInheritorOrSelf(
                                variableOrMethodClass, throwableClass, true)) {
                    continue;
                }
                if (!checkType(referencedType, weakestTypeClasses)) {
                    continue;
                }
                checked = true;
                break;
            }
            if (!checked) {
                return false;
            }
        }
        return true;
    }

    private static boolean throwsIncompatibleException(
            PsiMethod method, Collection<PsiClassType> exceptionTypes) {
        final PsiReferenceList superThrowsList =
                method.getThrowsList();
        final PsiClassType[] superThrownTypes =
                superThrowsList.getReferencedTypes();
        for (PsiClassType superThrownType : superThrownTypes) {
            if (exceptionTypes.contains(superThrownType)) {
                return true;
            }
            final PsiClass aClass = superThrownType.resolve();
            if (aClass == null) {
                return true;
            }
            if (!ClassUtils.isSubclass(aClass,
                    "java.lang.RuntimeException") &&
                    !ClassUtils.isSubclass(aClass,
                            "java.lang.Error")) {
                return true;
            }

        }
        return false;
    }

    private static boolean checkType(
            @Nullable PsiType type,
            @NotNull Collection<PsiClass> weakestTypeClasses) {
        if (!(type instanceof PsiClassType)) {
            return false;
        }
        final PsiClassType classType = (PsiClassType) type;
        final PsiClass aClass = classType.resolve();
        if (aClass == null) {
            return false;
        }
        checkClass(aClass, weakestTypeClasses);
        return true;
    }

    public static Set<PsiClass> filterAccessibleClasses(
            Set<PsiClass> weakestTypeClasses, PsiElement context) {
        final Set<PsiClass> result = new HashSet();
        for (PsiClass weakestTypeClass : weakestTypeClasses) {
            if (isAccessibleFrom(weakestTypeClass, context)) {
                result.add(weakestTypeClass);
                continue;
            }
            final PsiClass visibleInheritor =
                    getVisibleInheritor(weakestTypeClass, context);
            if (visibleInheritor != null) {
                result.add(visibleInheritor);
            }
        }
        return result;
    }

    @Nullable
    private static PsiClass getVisibleInheritor(PsiClass superClass,
                                                PsiElement context) {
        final Query<PsiClass> search =
                DirectClassInheritorsSearch.search(superClass,
                        context.getResolveScope());
        for (PsiClass aClass : search) {
            if (superClass.isInheritor(aClass, true)) {
                if (isAccessibleFrom(aClass, context)) {
                    return aClass;
                } else {
                    return getVisibleInheritor(aClass, context);
                }
            }
        }
        return null;
    }

    private static void checkClass(
            @NotNull PsiClass aClass,
            @NotNull Collection<PsiClass> weakestTypeClasses) {
        boolean shouldAdd = true;
        for (Iterator<PsiClass> iterator =
                weakestTypeClasses.iterator(); iterator.hasNext();) {
            final PsiClass weakestTypeClass = iterator.next();
            if (!weakestTypeClass.equals(aClass)) {
                if (aClass.isInheritor(weakestTypeClass, true)) {
                    iterator.remove();
                } else if (weakestTypeClass.isInheritor(aClass, true)) {
                    shouldAdd = false;
                } else {
                    iterator.remove();
                    shouldAdd = false;
                }
            } else {
                shouldAdd = false;
            }
        }
        if (shouldAdd) {
            weakestTypeClasses.add(aClass);
        }
    }

    private static boolean isAccessibleFrom(PsiClass aClass,
                                            PsiElement context){
        if(aClass.hasModifierProperty(PsiModifier.PUBLIC)){
            return true;
        }
        if(aClass.hasModifierProperty(PsiModifier.PRIVATE)){
            return false;
        }
        final PsiClass referencingClass =
                ClassUtils.getContainingClass(context);
        if (referencingClass == null){
            return false;
        }
        if(context.equals(aClass)){
            return true;
        }
        return ClassUtils.inSamePackage(aClass, referencingClass);
    }

    private static boolean isAccessibleFrom(PsiMethod method,
                                            PsiElement context) {
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) {
            return true;
        }
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            return false;
        }
        final PsiClass referencingClass =
                ClassUtils.getContainingClass(context);
        if (referencingClass == null) {
            return false;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (method.hasModifierProperty(PsiModifier.PROTECTED)) {
            if (referencingClass.isInheritor(containingClass, true)) {
                return true;
            }
        }
        return method.hasModifierProperty(PsiModifier.PACKAGE_LOCAL) &&
                ClassUtils.inSamePackage(referencingClass, containingClass);
    }


    private static int findElementIndexInExpressionList(
            @NotNull PsiElement element,
            @NotNull PsiExpression[] expressions) {
        for (int i = 0; i < expressions.length; i++) {
            final PsiExpression anExpression = expressions[i];
            if (element.equals(anExpression)) {
                return i;
            }
        }
        return -1;
    }
}
