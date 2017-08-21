/*
 * Copyright 2008-2017 Bas Leijdekkers
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

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Query;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class WeakestTypeFinder {

  private WeakestTypeFinder() {}

  @NotNull
  public static Collection<PsiClass> calculateWeakestClassesNecessary(@NotNull PsiElement variableOrMethod,
                                                                      boolean useRighthandTypeAsWeakestTypeInAssignments,
                                                                      boolean useParameterizedTypeForCollectionMethods) {
    final PsiType variableOrMethodType;
    if (variableOrMethod instanceof PsiVariable) {
      final PsiVariable variable = (PsiVariable)variableOrMethod;
      variableOrMethodType = variable.getType();
    }
    else if (variableOrMethod instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)variableOrMethod;
      variableOrMethodType = method.getReturnType();
      if (PsiType.VOID.equals(variableOrMethodType)) {
        return Collections.emptyList();
      }
    }
    else {
      throw new IllegalArgumentException("PsiMethod or PsiVariable expected: " + variableOrMethod);
    }
    if (!(variableOrMethodType instanceof PsiClassType)) {
      return Collections.emptyList();
    }
    final PsiClassType variableOrMethodClassType = (PsiClassType)variableOrMethodType;
    final PsiClass variableOrMethodClass = variableOrMethodClassType.resolve();
    if (variableOrMethodClass == null || variableOrMethodClass instanceof PsiTypeParameter) {
      return Collections.emptyList();
    }
    Set<PsiClass> weakestTypeClasses = new HashSet<>();
    final GlobalSearchScope scope = variableOrMethod.getResolveScope();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(variableOrMethod.getProject());
    final PsiClass lowerBoundClass;
    if (variableOrMethod instanceof PsiResourceVariable) {
      lowerBoundClass = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, scope);
      if (lowerBoundClass == null || variableOrMethodClass.equals(lowerBoundClass)) {
        return Collections.emptyList();
      }
      weakestTypeClasses.add(lowerBoundClass);
      final PsiResourceVariable resourceVariable = (PsiResourceVariable)variableOrMethod;
      @NonNls final String methodCallText = resourceVariable.getName() + ".close()";
      final PsiMethodCallExpression methodCallExpression =
        (PsiMethodCallExpression)facade.getElementFactory().createExpressionFromText(methodCallText, resourceVariable.getParent());
      if (!findWeakestType(methodCallExpression, weakestTypeClasses)) {
        return Collections.emptyList();
      }
      if (weakestTypeClasses.isEmpty()) {
        weakestTypeClasses.add(lowerBoundClass);
      }
    }
    else {
      lowerBoundClass = facade.findClass(CommonClassNames.JAVA_LANG_OBJECT, scope);
      if (lowerBoundClass == null || variableOrMethodClass.equals(lowerBoundClass)) {
        return Collections.emptyList();
      }
      weakestTypeClasses.add(lowerBoundClass);
    }

    final Query<PsiReference> query = ReferencesSearch.search(variableOrMethod, variableOrMethod.getUseScope());
    boolean hasUsages = false;
    for (PsiReference reference : query) {
      ProgressManager.checkCanceled();
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
      final PsiElement referenceGrandParent = referenceParent.getParent();
      if (reference instanceof PsiMethodReferenceExpression) {
        final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)reference;
        final PsiType type = methodReferenceExpression.getFunctionalInterfaceType();
        final PsiType returnType = LambdaUtil.getFunctionalInterfaceReturnType(type);
        if (!PsiType.VOID.equals(returnType) && !checkType(returnType, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      } else if (referenceParent instanceof PsiExpressionList) {
        if (!(referenceGrandParent instanceof PsiMethodCallExpression)) {
          return Collections.emptyList();
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)referenceGrandParent;
        if (!findWeakestType(referenceElement, methodCallExpression, useParameterizedTypeForCollectionMethods, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceGrandParent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)referenceGrandParent;
        if (PsiUtil.skipParenthesizedExprUp(methodCallExpression.getParent()) instanceof PsiTypeCastExpression || 
            !findWeakestType(methodCallExpression, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiAssignmentExpression) {
        final PsiAssignmentExpression assignmentExpression = (PsiAssignmentExpression)referenceParent;
        if (!findWeakestType(referenceElement, assignmentExpression, useRighthandTypeAsWeakestTypeInAssignments, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiVariable) {
        final PsiVariable variable = (PsiVariable)referenceParent;
        final PsiType type = variable.getType();
        if (!type.isAssignableFrom(variableOrMethodType) || !checkType(type, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiForeachStatement) {
        final PsiForeachStatement foreachStatement = (PsiForeachStatement)referenceParent;
        if (!Comparing.equal(foreachStatement.getIteratedValue(), referenceElement)) {
          return Collections.emptyList();
        }
        final PsiClass javaLangIterableClass = facade.findClass(CommonClassNames.JAVA_LANG_ITERABLE, scope);
        if (javaLangIterableClass == null) {
          return Collections.emptyList();
        }
        checkClass(javaLangIterableClass, weakestTypeClasses);
      }
      else if (referenceParent instanceof PsiReturnStatement) {
        final PsiType type = PsiTypesUtil.getMethodReturnType(referenceParent);
        if (!checkType(type, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiReferenceExpression) {
        // field access, method call is handled above.
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)referenceParent;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiField)) {
          return Collections.emptyList();
        }
        final PsiField field = (PsiField)target;
        final PsiClass containingClass = field.getContainingClass();
        checkClass(containingClass, weakestTypeClasses);
      }
      else if (referenceParent instanceof PsiArrayInitializerExpression) {
        final PsiArrayInitializerExpression arrayInitializerExpression = (PsiArrayInitializerExpression)referenceParent;
        if (!findWeakestType(arrayInitializerExpression, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiThrowStatement) {
        final PsiThrowStatement throwStatement = (PsiThrowStatement)referenceParent;
        if (!findWeakestType(throwStatement, variableOrMethodClass, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiConditionalExpression) {
        final PsiConditionalExpression conditionalExpression = (PsiConditionalExpression)referenceParent;
        final PsiExpression condition = conditionalExpression.getCondition();
        if (referenceElement.equals(condition)) {
          return Collections.emptyList();
        }
        final PsiType type = ExpectedTypeUtils.findExpectedType(conditionalExpression, true);
        if (!checkType(type, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiBinaryExpression) {
        // strings only
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)referenceParent;
        final PsiType type = binaryExpression.getType();
        if (variableOrMethodType.equals(type)) {
          if (!checkType(type, weakestTypeClasses)) {
            return Collections.emptyList();
          }
        }
      }
      else if (referenceParent instanceof PsiSwitchStatement) {
        // only enums and primitives can be a switch expression
        return Collections.emptyList();
      }
      else if (referenceParent instanceof PsiPrefixExpression) {
        // only primitives and boxed types are the target of a prefix
        // expression
        return Collections.emptyList();
      }
      else if (referenceParent instanceof PsiPostfixExpression) {
        // only primitives and boxed types are the target of a postfix
        // expression
        return Collections.emptyList();
      }
      else if (referenceParent instanceof PsiIfStatement) {
        // only booleans and boxed Booleans are the condition of an if
        // statement
        return Collections.emptyList();
      }
      else if (referenceParent instanceof PsiForStatement) {
        // only booleans and boxed Booleans are the condition of an
        // for statement
        return Collections.emptyList();
      }
      else if (referenceParent instanceof PsiNewExpression) {
        final PsiNewExpression newExpression = (PsiNewExpression)referenceParent;
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
          final PsiClass outerClass = innerClass.getContainingClass();
          if (outerClass != null) {
            checkClass(outerClass, weakestTypeClasses);
          }
        }
      }
      if (weakestTypeClasses.contains(variableOrMethodClass) || weakestTypeClasses.isEmpty()) {
        return Collections.emptyList();
      }
    }
    if (!hasUsages) {
      return Collections.emptyList();
    }
    weakestTypeClasses = filterAccessibleClasses(weakestTypeClasses, variableOrMethodClass, variableOrMethod);
    return weakestTypeClasses;
  }

  private static boolean findWeakestType(PsiElement referenceElement,
                                         PsiMethodCallExpression methodCallExpression,
                                         boolean useParameterizedTypeForCollectionMethods,
                                         Set<PsiClass> weakestTypeClasses) {
    if (!(referenceElement instanceof PsiExpression)) {
      return false;
    }
    final JavaResolveResult resolveResult = methodCallExpression.resolveMethodGenerics();
    final PsiMethod method = (PsiMethod)resolveResult.getElement();
    if (method == null) {
      return false;
    }
    final PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    final PsiExpressionList expressionList = methodCallExpression.getArgumentList();
    final PsiExpression[] expressions = expressionList.getExpressions();
    final int index = ArrayUtil.indexOf(expressions, referenceElement);
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
    }
    else {
      parameter = parameters[parameters.length - 1];
      type = parameter.getType();
      if (!(type instanceof PsiEllipsisType)) {
        return false;
      }
    }
    if (!useParameterizedTypeForCollectionMethods) {
      return checkType(type, substitutor, weakestTypeClasses);
    }
    @NonNls final String methodName = method.getName();
    if (HardcodedMethodConstants.REMOVE.equals(methodName) ||
        HardcodedMethodConstants.GET.equals(methodName) ||
        "containsKey".equals(methodName) ||
        "containsValue".equals(methodName) ||
        "contains".equals(methodName) ||
        HardcodedMethodConstants.INDEX_OF.equals(methodName) ||
        HardcodedMethodConstants.LAST_INDEX_OF.equals(methodName)) {
      final PsiClass containingClass = method.getContainingClass();
      if (InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_MAP) ||
          InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_COLLECTION)) {
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier != null) {
          final PsiType qualifierType = qualifier.getType();
          if (qualifierType instanceof PsiClassType) {
            final PsiClassType classType = (PsiClassType)qualifierType;
            final PsiType[] parameterTypes = classType.getParameters();
            if (parameterTypes.length > 0) {
              final PsiType parameterType = parameterTypes[0];
              final PsiExpression expression = expressions[index];
              final PsiType expressionType = expression.getType();
              if (expressionType == null || parameterType == null || !parameterType.isAssignableFrom(expressionType)) {
                return false;
              }
              return checkType(parameterType, substitutor, weakestTypeClasses);
            }
          }
        }
      }
    }
    return checkType(type, substitutor, weakestTypeClasses);
  }

  private static boolean checkType(@Nullable PsiType type, @NotNull PsiSubstitutor substitutor,
                                   @NotNull Collection<PsiClass> weakestTypeClasses) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    if (aClass instanceof PsiTypeParameter) {
      final PsiType substitution = substitutor.substitute((PsiTypeParameter)aClass);
      return checkType(substitution, weakestTypeClasses);
    }
    checkClass(aClass, weakestTypeClasses);
    return true;
  }

  private static boolean findWeakestType(PsiMethodCallExpression methodCallExpression, Set<PsiClass> weakestTypeClasses) {
    final PsiMethod method = methodCallExpression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] classTypes = throwsList.getReferencedTypes();
    final Collection<PsiClassType> thrownTypes = new HashSet<>(Arrays.asList(classTypes));
    final List<PsiMethod> superMethods = findAllSuperMethods(method);
    boolean checked = false;
    if (!superMethods.isEmpty()) {
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(methodCallExpression, false);
      for (PsiMethod superMethod : superMethods) {
        final PsiType returnType = superMethod.getReturnType();
        if (expectedType instanceof PsiClassType) {
          if (!(returnType instanceof PsiClassType)) {
            continue;
          }
          final PsiClassType expectedClassType = (PsiClassType)expectedType;
          expectedClassType.rawType().isAssignableFrom(returnType);
        }
        else if (expectedType != null && returnType != null && !expectedType.isAssignableFrom(returnType)) {
          continue;
        }
        if (throwsIncompatibleException(superMethod, thrownTypes)) {
          continue;
        }
        if (!PsiUtil.isAccessible(superMethod, methodCallExpression, null)) {
          continue;
        }
        final PsiClass containingClass = superMethod.getContainingClass();
        if (checkClass(containingClass, weakestTypeClasses)) {
          checked = true;
          break;
        }
      }
    }
    if (!checked) {
      if (TypeUtils.isTypeParameter(method.getReturnType())) {
        return false;
      }
      final PsiClass containingClass = method.getContainingClass();
      checkClass(containingClass, weakestTypeClasses);
    }
    return true;
  }

  private static List<PsiMethod> findAllSuperMethods(PsiMethod method) {
    final List<PsiMethod> result = new ArrayList<>();
    SuperMethodsSearch.search(method, null, true, false).forEach(method12 -> {
      result.add(method12.getMethod());
      return true;
    });
    Collections.sort(result, (method1, method2) -> {
      // methods from deepest super classes first
      final PsiClass aClass1 = method1.getContainingClass();
      final PsiClass aClass2 = method2.getContainingClass();
      if (aClass1 == null || aClass2 == null || aClass1.equals(aClass2)) {
        return 0;
      } else if (aClass1.isInterface() && !aClass2.isInterface()) {
        return -1;
      } else if (!aClass1.isInterface() && aClass2.isInterface()) {
        return 1;
      } else if (aClass1.isInheritor(aClass2, true)) {
        return 1;
      } else if (aClass2.isInheritor(aClass1, true)) {
        return -1;
      }
      final String name1 = aClass1.getName();
      final String name2 = aClass2.getName();
      return name1.compareTo(name2);
    });
    return result;
  }

  private static boolean findWeakestType(PsiElement referenceElement, PsiAssignmentExpression assignmentExpression,
                                         boolean useRighthandTypeAsWeakestTypeInAssignments, Set<PsiClass> weakestTypeClasses) {
    final IElementType tokenType = assignmentExpression.getOperationTokenType();
    if (JavaTokenType.EQ != tokenType) {
      return false;
    }
    final PsiExpression lhs = assignmentExpression.getLExpression();
    final PsiExpression rhs = assignmentExpression.getRExpression();
    if (rhs == null) {
      return false;
    }
    final PsiType lhsType = lhs.getType();
    final PsiType rhsType = rhs.getType();
    if (lhsType == null || rhsType == null || !lhsType.isAssignableFrom(rhsType)) {
      return false;
    }
    if (referenceElement.equals(rhs)) {
      if (!checkType(lhsType, weakestTypeClasses)) {
        return false;
      }
    }
    else if (useRighthandTypeAsWeakestTypeInAssignments &&
             (!(rhs instanceof PsiNewExpression) || !(rhs instanceof PsiTypeCastExpression)) &&
             lhsType.equals(rhsType)) {
      return false;
    }
    return true;
  }

  private static boolean findWeakestType(PsiArrayInitializerExpression arrayInitializerExpression, Set<PsiClass> weakestTypeClasses) {
    final PsiType type = arrayInitializerExpression.getType();
    if (!(type instanceof PsiArrayType)) {
      return false;
    }
    final PsiArrayType arrayType = (PsiArrayType)type;
    final PsiType componentType = arrayType.getComponentType();
    return checkType(componentType, weakestTypeClasses);
  }

  private static boolean findWeakestType(PsiThrowStatement throwStatement, PsiClass variableOrMethodClass,
                                         Set<PsiClass> weakestTypeClasses) {
    final PsiClassType runtimeExceptionType = TypeUtils.getType(CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, throwStatement);
    final PsiClass runtimeExceptionClass = runtimeExceptionType.resolve();
    if (runtimeExceptionClass != null && InheritanceUtil.isInheritorOrSelf(variableOrMethodClass, runtimeExceptionClass, true)) {
      if (!checkType(runtimeExceptionType, weakestTypeClasses)) {
        return false;
      }
    }
    else {
      final PsiMethod method = PsiTreeUtil.getParentOfType(throwStatement, PsiMethod.class);
      if (method == null) {
        return false;
      }
      final PsiReferenceList throwsList = method.getThrowsList();
      final PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
      boolean checked = false;
      for (PsiClassType referencedType : referencedTypes) {
        final PsiClass throwableClass = referencedType.resolve();
        if (throwableClass == null ||
            !InheritanceUtil.isInheritorOrSelf(variableOrMethodClass, throwableClass, true)) {
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

  private static boolean throwsIncompatibleException(PsiMethod method, Collection<PsiClassType> exceptionTypes) {
    final PsiReferenceList superThrowsList = method.getThrowsList();
    final PsiClassType[] superThrownTypes = superThrowsList.getReferencedTypes();
    outer:
    for (PsiClassType superThrownType : superThrownTypes) {
      if (exceptionTypes.contains(superThrownType)) {
        continue;
      }
      for (PsiClassType exceptionType : exceptionTypes) {
        if (InheritanceUtil.isInheritor(superThrownType, exceptionType.getCanonicalText())) {
          continue outer;
        }
      }
      final PsiClass aClass = superThrownType.resolve();
      if (aClass == null) {
        return true;
      }
      if (!InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION) &&
          !InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_LANG_ERROR)) {
        return true;
      }
    }
    return false;
  }

  @Contract("null, _ -> false")
  private static boolean checkType(@Nullable PsiType type, @NotNull Collection<PsiClass> weakestTypeClasses) {
    if (!(type instanceof PsiClassType)) {
      return false;
    }
    final PsiClassType classType = (PsiClassType)type;
    final PsiClass aClass = classType.resolve();
    if (aClass == null) {
      return false;
    }
    checkClass(aClass, weakestTypeClasses);
    return true;
  }

  public static Set<PsiClass> filterAccessibleClasses(Set<PsiClass> weakestTypeClasses, PsiClass upperBound, PsiElement context) {
    final Set<PsiClass> result = new HashSet<>();
    for (PsiClass weakestTypeClass : weakestTypeClasses) {
      if (PsiUtil.isAccessible(weakestTypeClass, context, null) && !weakestTypeClass.isDeprecated()) {
        result.add(weakestTypeClass);
        continue;
      }
      final PsiClass visibleInheritor = getVisibleInheritor(weakestTypeClass, upperBound, context);
      if (visibleInheritor != null) {
        result.add(visibleInheritor);
      }
    }
    return result;
  }

  @Nullable
  private static PsiClass getVisibleInheritor(@NotNull PsiClass superClass, PsiClass upperBound, PsiElement context) {
    final Query<PsiClass> search = DirectClassInheritorsSearch.search(superClass, context.getResolveScope());
    final Project project = superClass.getProject();
    for (PsiClass aClass : search) {
      if (aClass.isInheritor(superClass, true) && upperBound.isInheritor(aClass, true)) {
        if (PsiUtil.isAccessible(project, aClass, context, null)) {
          return aClass;
        }
        else {
          return getVisibleInheritor(aClass, upperBound, context);
        }
      }
    }
    return null;
  }

  private static boolean checkClass(@Nullable PsiClass aClass, @NotNull Collection<PsiClass> weakestTypeClasses) {
    if (aClass == null) {
      return false;
    }
    boolean shouldAdd = true;
    for (final Iterator<PsiClass> iterator = weakestTypeClasses.iterator(); iterator.hasNext(); ) {
      final PsiClass weakestTypeClass = iterator.next();
      if (weakestTypeClass.equals(aClass)) {
        return true;
      }
      if (aClass.isInheritor(weakestTypeClass, true)) {
        iterator.remove();
      }
      else if (weakestTypeClass.isInheritor(aClass, true)) {
        shouldAdd = false;
      }
      else {
        iterator.remove();
        shouldAdd = false;
      }
    }
    if (!shouldAdd) {
      return false;
    }
    weakestTypeClasses.add(aClass);
    return true;
  }
}
