// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.containers.ContainerUtil;
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
    final PsiClass variableOrMethodClass = PsiUtil.resolveClassInClassTypeOnly(variableOrMethodType);
    if (variableOrMethodClass == null || variableOrMethodClass instanceof PsiTypeParameter) {
      return Collections.emptyList();
    }
    final Set<PsiClass> weakestTypeClasses = new HashSet<>();
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
      hasUsages = true;
      PsiElement referenceElement = reference.getElement();
      PsiElement referenceParent = PsiUtil.skipParenthesizedExprUp(referenceElement.getParent());
      if (referenceParent instanceof PsiMethodCallExpression) {
        referenceElement = referenceParent;
        referenceParent = PsiUtil.skipParenthesizedExprUp(referenceElement.getParent());
      }
      final PsiElement referenceGrandParent = referenceParent.getParent();
      if (referenceElement instanceof PsiMethodReferenceExpression) {
        final PsiMethodReferenceExpression methodReferenceExpression = (PsiMethodReferenceExpression)referenceElement;
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
      else if (referenceParent instanceof PsiLambdaExpression) {
        final PsiLambdaExpression lambda = (PsiLambdaExpression)referenceParent;
        final PsiClassType returnType = ObjectUtils.tryCast(LambdaUtil.getFunctionalInterfaceReturnType(lambda), PsiClassType.class);
        checkType(returnType, weakestTypeClasses);
      }
      else if (referenceGrandParent instanceof PsiMethodCallExpression) {
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)referenceGrandParent;
        if (PsiUtil.skipParenthesizedExprUp(methodCallExpression.getParent()) instanceof PsiTypeCastExpression ||
            !findWeakestType(methodCallExpression, weakestTypeClasses)) {
          return Collections.emptyList();
        }
      }
      else if (referenceParent instanceof PsiResourceExpression) {
        final PsiClass closeable = facade.findClass(CommonClassNames.JAVA_LANG_AUTO_CLOSEABLE, scope);
        checkClass(closeable, weakestTypeClasses);
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
      else if (referenceParent instanceof PsiUnaryExpression) {
        // only primitives and boxed types are the target of an unary
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
          final PsiClass innerClass = PsiUtil.resolveClassInClassTypeOnly(newExpression.getType());
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
    return filterAccessibleClasses(weakestTypeClasses, variableOrMethodClass, variableOrMethod);
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
    if (parameterList.isEmpty()) {
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
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
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
    final Collection<PsiClassType> thrownTypes = ContainerUtil.set(classTypes);
    final List<PsiMethod> superMethods = findAllSuperMethods(method);
    boolean checked = false;
    if (!superMethods.isEmpty()) {
      final PsiType expectedType = ExpectedTypeUtils.findExpectedType(methodCallExpression, false);
      for (PsiMethod superMethod : superMethods) {
        ProgressManager.checkCanceled();
        final PsiType returnType = superMethod.getReturnType();
        if (expectedType instanceof PsiClassType) {
          if (!(returnType instanceof PsiClassType)) {
            continue;
          }
          final PsiClassType expectedClassType = (PsiClassType)expectedType;
          if (!expectedClassType.rawType().isAssignableFrom(returnType)) continue;
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
      ProgressManager.checkCanceled();
      result.add(method12.getMethod());
      return true;
    });
    result.sort((method1, method2) -> {
      // methods from deepest super classes first
      final PsiClass aClass1 = method1.getContainingClass();
      final PsiClass aClass2 = method2.getContainingClass();
      if (aClass1 == null || aClass2 == null || aClass1.equals(aClass2)) {
        return 0;
      }
      if (aClass1.isInterface() && !aClass2.isInterface()) {
        return -1;
      }
      if (!aClass1.isInterface() && aClass2.isInterface()) {
        return 1;
      }
      if (aClass1.isInheritor(aClass2, true)) {
        return 1;
      }
      if (aClass2.isInheritor(aClass1, true)) {
        return -1;
      }
      final String name1 = aClass1.getName();
      final String name2 = aClass2.getName();
      return StringUtil.compare(name1, name2, false);
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
      return checkType(lhsType, weakestTypeClasses);
    }
    return !useRighthandTypeAsWeakestTypeInAssignments ||
           rhs instanceof PsiNewExpression && rhs instanceof PsiTypeCastExpression ||
           !lhsType.equals(rhsType);
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
      return checkType(runtimeExceptionType, weakestTypeClasses);
    }
    final PsiMethod method = PsiTreeUtil.getParentOfType(throwStatement, PsiMethod.class);
    if (method == null) {
      return false;
    }
    final PsiReferenceList throwsList = method.getThrowsList();
    final PsiClassType[] referencedTypes = throwsList.getReferencedTypes();
    boolean checked = false;
    for (PsiClassType referencedType : referencedTypes) {
      ProgressManager.checkCanceled();
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
    return checked;
  }

  private static boolean throwsIncompatibleException(PsiMethod method, Collection<? extends PsiClassType> exceptionTypes) {
    final PsiReferenceList superThrowsList = method.getThrowsList();
    final PsiClassType[] superThrownTypes = superThrowsList.getReferencedTypes();
    outer:
    for (PsiClassType superThrownType : superThrownTypes) {
      ProgressManager.checkCanceled();
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
    final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(type);
    if (aClass == null) {
      return false;
    }
    checkClass(aClass, weakestTypeClasses);
    return true;
  }

  private static Set<PsiClass> filterAccessibleClasses(Set<? extends PsiClass> weakestTypeClasses, PsiClass upperBound, PsiElement context) {
    final Set<PsiClass> result = new HashSet<>();
    for (PsiClass weakestTypeClass : weakestTypeClasses) {
      ProgressManager.checkCanceled();
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
      ProgressManager.checkCanceled();
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
      ProgressManager.checkCanceled();
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
