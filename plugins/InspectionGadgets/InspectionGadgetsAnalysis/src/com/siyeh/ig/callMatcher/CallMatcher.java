/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.siyeh.ig.callMatcher;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.MethodCallUtils;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This interface represents a condition upon method call
 *
 * @author Tagir Valeev
 */
public interface CallMatcher extends Predicate<PsiMethodCallExpression> {
  /**
   * @return names of the methods for which this matcher may return true. For any other method it guaranteed to return false
   */
  Stream<String> names();

  @Contract("null -> false")
  boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef);

  @Contract("null -> false")
  boolean test(@Nullable PsiMethodCallExpression call);

  @Contract("null -> false")
  boolean methodMatches(@Nullable PsiMethod method);

  /**
   * Returns true if the supplied expression is (possibly parenthesized) method call which matches this matcher
   *
   * @param expression expression to test
   * @return true if the supplied expression matches this matcher
   */
  @Contract("null -> false")
  default boolean matches(@Nullable PsiExpression expression) {
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    return expression instanceof PsiMethodCallExpression && test((PsiMethodCallExpression)expression);
  }

  /**
   * Returns a new matcher which will return true if any of supplied matchers return true
   *
   * @param matchers
   * @return a new matcher
   */
  static CallMatcher anyOf(CallMatcher... matchers) {
    return new CallMatcher() {
      @Override
      public Stream<String> names() {
        return Stream.of(matchers).flatMap(CallMatcher::names);
      }

      @Override
      public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
        for (CallMatcher m : matchers) {
          if (m.methodReferenceMatches(methodRef)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean methodMatches(PsiMethod method) {
        for (CallMatcher m : matchers) {
          if (m.methodMatches(method)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public boolean test(PsiMethodCallExpression call) {
        for (CallMatcher m : matchers) {
          if (m.test(call)) {
            return true;
          }
        }
        return false;
      }

      @Override
      public String toString() {
        return Stream.of(matchers).map(CallMatcher::toString).collect(Collectors.joining(" or ", "{", "}"));
      }
    };
  }

  /**
   * Creates a matcher which matches an instance method having one of supplied names which class (or any of superclasses) is className
   *
   * @param className fully-qualified class name
   * @param methodNames names of the methods
   * @return a new matcher
   */
  static Simple instanceCall(@NotNull String className, String... methodNames) {
    return new Simple(className, ContainerUtil.newTroveSet(methodNames), null, false);
  }

  /**
   * Creates a matcher which matches a static method having one of supplied names which class is className
   *
   * @param className fully-qualified class name
   * @param methodNames names of the methods
   * @return a new matcher
   */
  static Simple staticCall(@NotNull String className, String... methodNames) {
    return new Simple(className, ContainerUtil.newTroveSet(methodNames), null, true);
  }

  class Simple implements CallMatcher {
    private final @NotNull String myClassName;
    private final @NotNull Set<String> myNames;
    private final @Nullable String[] myParameters;
    private final boolean myStatic;

    private Simple(@NotNull String className, @NotNull Set<String> names, @Nullable String[] parameters, boolean aStatic) {
      myClassName = className;
      myNames = names;
      myParameters = parameters;
      myStatic = aStatic;
    }

    @Override
    public Stream<String> names() {
      return myNames.stream();
    }

    /**
     * Creates a new matcher which in addition to current matcher checks the number of parameters of the called method
     *
     * @param count expected number of parameters
     * @return a new matcher
     * @throws IllegalStateException if this matcher is already limited to parameters count or types
     */
    public Simple parameterCount(int count) {
      if (myParameters != null) {
        throw new IllegalStateException("Parameter count is already set to " + count);
      }
      return new Simple(myClassName, myNames, count == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : new String[count], myStatic);
    }

    /**
     * Creates a new matcher which in addition to current matcher checks the number of parameters of the called method
     * and their types
     *
     * @param types textual representation of parameter types (may contain null to ignore checking parameter type of specific argument)
     * @return a new matcher
     * @throws IllegalStateException if this matcher is already limited to parameters count or types
     */
    public Simple parameterTypes(@NotNull String... types) {
      if (myParameters != null) {
        throw new IllegalStateException("Parameters are already registered");
      }
      return new Simple(myClassName, myNames, types.length == 0 ? ArrayUtil.EMPTY_STRING_ARRAY : types.clone(), myStatic);
    }

    private static boolean parameterTypeMatches(String type, PsiParameter parameter) {
      if (type == null) return true;
      PsiType psiType = parameter.getType();
      return psiType.equalsToText(type) ||
             psiType instanceof PsiClassType && ((PsiClassType)psiType).rawType().equalsToText(type);
    }

    @Override
    public boolean methodReferenceMatches(PsiMethodReferenceExpression methodRef) {
      if (methodRef == null) return false;
      String name = methodRef.getReferenceName();
      if (!myNames.contains(name)) return false;
      PsiMethod method = ObjectUtils.tryCast(methodRef.resolve(), PsiMethod.class);
      if (!methodMatches(method)) return false;
      PsiParameterList parameterList = method.getParameterList();
      return parametersMatch(parameterList);
    }

    @Override
    public boolean test(PsiMethodCallExpression call) {
      if (call == null) return false;
      String name = call.getMethodExpression().getReferenceName();
      if (!myNames.contains(name)) return false;
      PsiExpression[] args = call.getArgumentList().getExpressions();
      if (myParameters != null && myParameters.length > 0) {
        if (args.length < myParameters.length) return false;
      }
      PsiMethod method = call.resolveMethod();
      if (method == null) return false;
      PsiParameterList parameterList = method.getParameterList();
      if (parameterList.getParametersCount() > args.length ||
          (!MethodCallUtils.isVarArgCall(call) && parameterList.getParametersCount() < args.length)) {
        return false;
      }
      return methodMatches(method);
    }

    private boolean parametersMatch(@NotNull PsiParameterList parameterList) {
      if (myParameters == null) return true;
      if (myParameters.length != parameterList.getParametersCount()) return false;
      return StreamEx.zip(myParameters, parameterList.getParameters(),
                          Simple::parameterTypeMatches).allMatch(Boolean.TRUE::equals);
    }

    @Contract("null -> false")
    public boolean methodMatches(PsiMethod method) {
      if (method == null) return false;
      PsiClass aClass = method.getContainingClass();
      if (aClass == null) return false;
      if (myStatic != method.getModifierList().hasExplicitModifier(PsiModifier.STATIC) ||
          (myStatic && !myClassName.equals(aClass.getQualifiedName())) ||
          (!myStatic && !InheritanceUtil.isInheritor(aClass, myClassName))) {
        return false;
      }
      return parametersMatch(method.getParameterList());
    }

    @Override
    public String toString() {
      return myClassName + "." + String.join("|", myNames);
    }
  }
}