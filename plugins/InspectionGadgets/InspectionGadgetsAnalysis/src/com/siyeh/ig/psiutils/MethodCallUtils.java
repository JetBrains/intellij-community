/*
 * Copyright 2003-2019 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.java.inst.MethodCallInstruction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.augment.PsiExtensionMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.JavaPsiConstructorUtil;
import com.siyeh.HardcodedMethodConstants;
import gnu.trove.THashSet;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.tryCast;

public final class MethodCallUtils {

  @NonNls private static final Set<String> regexMethodNames = new HashSet<>(5);

  static {
    regexMethodNames.add("compile");
    regexMethodNames.add("matches");
    regexMethodNames.add("replaceFirst");
    regexMethodNames.add("replaceAll");
    regexMethodNames.add("split");
  }

  private MethodCallUtils() {}

  public static @Nullable @NonNls String getMethodName(@NotNull PsiMethodCallExpression expression) {
    return expression.getMethodExpression().getReferenceName();
  }

  @Nullable
  public static PsiType getTargetType(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    return qualifierExpression.getType();
  }

  @Contract(pure = true)
  public static boolean isCompareToCall(@NotNull PsiMethodCallExpression expression) {
    if (!HardcodedMethodConstants.COMPARE_TO.equals(getMethodName(expression))) {
      return false;
    }
    return MethodUtils.isCompareTo(expression.resolveMethod());
  }

  @Contract(pure = true)
  public static boolean isCompareToIgnoreCaseCall(@NotNull PsiMethodCallExpression expression) {
    if (!"compareToIgnoreCase".equals(getMethodName(expression))) {
      return false;
    }
    return MethodUtils.isCompareToIgnoreCase(expression.resolveMethod());
  }

  @Contract(pure = true)
  public static boolean isEqualsCall(PsiMethodCallExpression expression) {
    if (!HardcodedMethodConstants.EQUALS.equals(getMethodName(expression))) {
      return false;
    }
    return MethodUtils.isEquals(expression.resolveMethod());
  }

  @Contract(pure = true)
  public static boolean isEqualsIgnoreCaseCall(PsiMethodCallExpression expression) {
    if (!HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(getMethodName(expression))) {
      return false;
    }
    return MethodUtils.isEqualsIgnoreCase(expression.resolveMethod());
  }

  @Contract(pure = true)
  public static boolean isSimpleCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, @NonNls String @Nullable ... parameterTypeStrings) {
    if (parameterTypeStrings == null) {
      return isCallToMethod(expression, calledOnClassName, returnType, methodName, (PsiType[])null);
    }
    if (!checkMethodName(expression, methodName)) return false;
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(expression.getProject());
    final PsiElementFactory factory = psiFacade.getElementFactory();
    final PsiType[] parameterTypes = PsiType.createArray(parameterTypeStrings.length);
    final GlobalSearchScope scope = expression.getResolveScope();
    for (int i = 0; i < parameterTypeStrings.length; i++) {
      final String parameterTypeString = parameterTypeStrings[i];
      parameterTypes[i] = factory.createTypeByFQClassName(parameterTypeString, scope);
    }
    return isCallToMethod(expression, calledOnClassName, returnType, methodName, parameterTypes);
  }

  @Contract(pure = true)
  public static boolean isCallToStaticMethod(@NotNull PsiMethodCallExpression expression, @NonNls @NotNull String calledOnClassName,
                                             @NonNls @NotNull String methodName, int parameterCount) {
    PsiExpression[] args = expression.getArgumentList().getExpressions();
    if (!methodName.equals(getMethodName(expression)) || args.length < parameterCount) {
      return false;
    }
    PsiMethod method = expression.resolveMethod();
    if (method == null ||
        !method.hasModifierProperty(PsiModifier.STATIC) ||
        method.getParameterList().getParametersCount() != parameterCount ||
        !method.isVarArgs() && args.length != parameterCount) {
      return false;
    }
    PsiClass aClass = method.getContainingClass();
    return aClass != null && calledOnClassName.equals(aClass.getQualifiedName());
  }

  @Contract(pure = true)
  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @Nullable Pattern methodNamePattern, PsiType @Nullable ... parameterTypes) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (methodNamePattern != null) {
      final String referenceName = methodExpression.getReferenceName();
      if (referenceName == null) {
        return false;
      }
      final Matcher matcher = methodNamePattern.matcher(referenceName);
      if (!matcher.matches()) {
        return false;
      }
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    if (calledOnClassName != null) {
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null) {
        if (!TypeUtils.expressionHasTypeOrSubtype(qualifier, calledOnClassName)) {
          return false;
        }
        return MethodUtils.methodMatches(method, null, returnType, methodNamePattern, parameterTypes);
      }
    }
    return MethodUtils.methodMatches(method, calledOnClassName, returnType, methodNamePattern, parameterTypes);
  }

  @Contract(pure = true)
  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, PsiType @Nullable ... parameterTypes) {
    if (!checkMethodName(expression, methodName)) return false;
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    return MethodUtils.methodMatches(method, calledOnClassName, returnType, methodName, parameterTypes);
  }

  private static boolean checkMethodName(@NotNull PsiMethodCallExpression expression,
                                         @Nullable @NonNls String methodName) {
    return methodName == null || methodName.equals(getMethodName(expression));
  }

  @Contract(pure = true)
  public static boolean isCallToRegexMethod(PsiMethodCallExpression expression) {
    if (!regexMethodNames.contains(getMethodName(expression))) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) {
      return false;
    }
    final String className = containingClass.getQualifiedName();
    return CommonClassNames.JAVA_LANG_STRING.equals(className) || "java.util.regex.Pattern".equals(className);
  }

  @Contract(pure = true)
  public static boolean isCallDuringObjectConstruction(PsiMethodCallExpression expression) {
    PsiExpression qualifier = expression.getMethodExpression().getQualifierExpression();
    if (qualifier != null && !(qualifier instanceof PsiQualifiedExpression)) {
      return false;
    }
    final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (member == null) {
      return false;
    }
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (member instanceof PsiClassInitializer || member instanceof PsiField) {
      return !member.hasModifierProperty(PsiModifier.STATIC);
    }
    if (member instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)member;
      if (method.isConstructor()) {
        return true;
      }
      if (CloneUtils.isClone(method)) {
        return true;
      }
      if (MethodUtils.simpleMethodMatches(method, null, "void", "readObject", "java.io.ObjectInputStream")) {
        return true;
      }
      return MethodUtils.simpleMethodMatches(method, null, "void", "readObjectNoData");
    }
    return false;
  }

  @Contract(pure = true)
  public static boolean isMethodCallOnVariable(@NotNull PsiMethodCallExpression expression,
                                               @NotNull PsiVariable variable,
                                               @NotNull @NonNls String methodName) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String name = methodExpression.getReferenceName();
    if (!methodName.equals(name)) {
      return false;
    }
    final PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(methodExpression.getQualifierExpression());
    if (!(qualifier instanceof PsiReferenceExpression)) {
      return false;
    }
    final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifier;
    final PsiElement element = referenceExpression.resolve();
    return variable.equals(element);
  }

  @Nullable
  public static PsiMethod findMethodWithReplacedArgument(@NotNull PsiCall call, @NotNull PsiExpression target,
                                                         @NotNull PsiExpression replacement) {
    final PsiExpressionList argumentList = call.getArgumentList();
    if (argumentList == null) {
      return null;
    }
    final PsiExpression[] expressions = argumentList.getExpressions();
    int index = -1;
    for (int i = 0; i < expressions.length; i++) {
      final PsiExpression expression = expressions[i];
      if (expression == target) {
        index = i;
        break;
      }
    }
    if (index < 0) {
      return null;
    }
    final PsiCall copy = (PsiCall)call.copy();
    final PsiExpressionList copyArgumentList = copy.getArgumentList();
    assert copyArgumentList != null;
    final PsiExpression[] arguments = copyArgumentList.getExpressions();
    arguments[index].replace(replacement);
    if (call instanceof PsiEnumConstant) {
      final PsiClass containingClass = ((PsiEnumConstant)call).getContainingClass();
      assert containingClass != null;
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(call.getProject());
      final PsiClassType type = facade.getElementFactory().createType(containingClass);
      final JavaResolveResult resolveResult = facade.getResolveHelper().resolveConstructor(type, copy.getArgumentList(), call);
      return (PsiMethod)resolveResult.getElement();
    }
    return copy.resolveMethod();
  }

  /**
   * Checks if the specified expression is an argument for any method call (skipping parentheses in between).
   * If the method call is found, checks if same method is called when argument is replaced with replacement.
   * @param expression  the expression to check
   * @param replacement  the replacement to replace expression with
   * @return true, if method was found and a different method was called with replacement. false, otherwise.
   */
  @Contract(pure = true)
  public static boolean isNecessaryForSurroundingMethodCall(PsiExpression expression, PsiExpression replacement) {
    PsiElement parent = expression.getParent();
    while (parent instanceof PsiParenthesizedExpression) {
      expression = (PsiExpression)parent;
      parent = parent.getParent();
    }
    if (!(parent instanceof PsiExpressionList)) {
      return false;
    }
    final PsiElement grandParent = parent.getParent();
    if (!(grandParent instanceof PsiCall)) {
      return false;
    }
    final PsiCall call = (PsiCall)grandParent;
    return call.resolveMethod() != findMethodWithReplacedArgument(call, expression, replacement);
  }

  @Contract(pure = true)
  public static boolean isSuperMethodCall(@NotNull PsiMethodCallExpression expression, @NotNull PsiMethod method) {
    if (!hasSuperQualifier(expression)) return false;
    final PsiMethod targetMethod = expression.resolveMethod();
    return targetMethod != null && MethodSignatureUtil.isSuperMethod(targetMethod, method);
  }

  @Contract(pure = true)
  public static boolean hasSuperQualifier(@NotNull PsiMethodCallExpression expression) {
    return PsiUtil.skipParenthesizedExprDown(expression.getMethodExpression().getQualifierExpression()) instanceof PsiSuperExpression;
  }

  /**
   * Returns true if given method call is a var-arg call
   *
   * @param call a call to test
   * @return true if call is resolved to the var-arg method and var-arg form is actually used
   */
  @Contract(pure = true)
  public static boolean isVarArgCall(PsiCall call) {
    JavaResolveResult result = call.resolveMethodGenerics();
    PsiMethod method = tryCast(result.getElement(), PsiMethod.class);
    if(method == null || !method.isVarArgs()) return false;
    PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpressionList argumentList = call.getArgumentList();
    return argumentList != null &&
           MethodCallInstruction
             .isVarArgCall(method, substitutor, argumentList.getExpressions(), method.getParameterList().getParameters());
  }

  @Contract(pure = true)
  public static boolean containsSuperMethodCall(@NotNull PsiMethod method) {
    final SuperCallVisitor visitor = new SuperCallVisitor(method);
    method.accept(visitor);
    return visitor.isSuperCallFound();
  }

  @Contract(pure = true)
  public static boolean callWithNonConstantString(@NotNull PsiMethodCallExpression expression, boolean considerStaticFinalConstant,
                                                  @NonNls String className, @NonNls String... methodNames) {
    final String methodName = getMethodName(expression);
    boolean found = false;
    for (String name : methodNames) {
      if (name.equals(methodName)) {
        found = true;
        break;
      }
    }
    if (!found) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) {
      return false;
    }
    if (!com.intellij.psi.util.InheritanceUtil.isInheritor(aClass, className)) {
      return false;
    }
    final PsiExpressionList argumentList = expression.getArgumentList();
    final PsiExpression argument = PsiUtil.skipParenthesizedExprDown(ExpressionUtils.getFirstExpressionInList(argumentList));
    if (argument == null) {
      return false;
    }
    final PsiType type = argument.getType();
    if (type == null || !type.equalsToText(CommonClassNames.JAVA_LANG_STRING)) {
      return false;
    }
    if (considerStaticFinalConstant && argument instanceof PsiReferenceExpression) {
      final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)argument;
      final PsiElement target = referenceExpression.resolve();
      if (target instanceof PsiField) {
        final PsiField field = (PsiField)target;
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
          return false;
        }
      }
    }
    return !PsiUtil.isConstantExpression(argument);
  }

  /**
   * For given method call, returns a qualifier if it's also a method call, or null otherwise
   *
   * @param methodCall call to check
   * @return a qualifier call
   */
  @Nullable
  @Contract(pure = true)
  public static PsiMethodCallExpression getQualifierMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    return
      tryCast(PsiUtil.skipParenthesizedExprDown(methodCall.getMethodExpression().getQualifierExpression()), PsiMethodCallExpression.class);
  }

  @Contract(pure = true)
  public static boolean isUsedAsSuperConstructorCallArgument(@NotNull PsiParameter parameter, boolean superMustBeLibrary) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod) || !((PsiMethod)scope).isConstructor()) {
      return false;
    }
    PsiMethod method = (PsiMethod)scope;
    final Set<PsiMethod> checked = new THashSet<>();

    while (true) {
      ProgressManager.checkCanceled();
      if (!checked.add(method)) {
        // we've already seen this method -> circular call chain
        return false;
      }
      final PsiMethodCallExpression call = JavaPsiConstructorUtil.findThisOrSuperCallInConstructor(method);
      if (call == null) {
        return false;
      }
      final int index = getParameterReferenceIndex(call, parameter);
      if (index < 0) {
        return false;
      }
      final JavaResolveResult resolveResult = call.resolveMethodGenerics();
      if (!resolveResult.isValidResult()) {
        return false;
      }
      method = (PsiMethod)resolveResult.getElement();
      if (method == null) {
        return false;
      }
      if (JavaPsiConstructorUtil.isSuperConstructorCall(call) && (!superMustBeLibrary || method instanceof PsiCompiledElement)) {
        return true;
      }
      final PsiParameter[] parameters = method.getParameterList().getParameters();
      parameter = parameters[Math.min(index, parameters.length - 1)];
    }
  }

  /**
   * Returns a method/constructor parameter which corresponds to given argument
   * @param argument an argument to find the corresponding parameter
   * @return a parameter or null if supplied expression is not a call argument, call is not resolved or expression is a var-arg
   * argument.
   */
  @Nullable
  public static PsiParameter getParameterForArgument(@NotNull PsiExpression argument) {
    PsiElement argumentParent = argument.getParent();
    if (argumentParent instanceof PsiReferenceExpression) {
      PsiMethodCallExpression callForQualifier = tryCast(argumentParent.getParent(), PsiMethodCallExpression.class);
      if (callForQualifier != null) {
        PsiMethod method = callForQualifier.resolveMethod();
        if (method instanceof PsiExtensionMethod) {
          return ((PsiExtensionMethod)method).getTargetReceiverParameter();
        }
      }
    }
    PsiExpressionList argList = tryCast(argumentParent, PsiExpressionList.class);
    if (argList == null) return null;
    PsiElement parent = argList.getParent();
    if (parent instanceof PsiAnonymousClass) {
      parent = parent.getParent();
    }
    PsiCall call = tryCast(parent, PsiCall.class);
    if (call == null) return null;
    PsiExpression[] args = argList.getExpressions();
    int index = ArrayUtil.indexOf(args, argument);
    if (index == -1) return null;
    PsiMethod method = call.resolveMethod();
    if (method == null) return null;
    PsiParameterList list = method.getParameterList();
    int count = list.getParametersCount();
    if (index >= count) return null;
    if (isVarArgCall(call) && index >= count - 1) return null;
    return method instanceof PsiExtensionMethod ? ((PsiExtensionMethod)method).getTargetParameter(index) : list.getParameter(index);
  }

  private static int getParameterReferenceIndex(PsiMethodCallExpression call, PsiParameter parameter) {
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      argument = PsiUtil.skipParenthesizedExprDown(argument);
      if (argument instanceof PsiReferenceExpression) {
        final PsiElement target = ((PsiReferenceExpression)argument).resolve();
        if (target == parameter) {
          return i;
        }
      }
    }
    return -1;
  }

  private static class SuperCallVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiMethod myMethod;
    private boolean mySuperCallFound;

    SuperCallVisitor(@NotNull PsiMethod method) {
      this.myMethod = method;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!mySuperCallFound) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitIfStatement(PsiIfStatement statement) {
      final PsiExpression condition = statement.getCondition();
      final Object result = ExpressionUtils.computeConstantExpression(condition);
      if (result != null && result.equals(Boolean.FALSE)) {
        return;
      }
      super.visitIfStatement(statement);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (mySuperCallFound) {
        return;
      }
      super.visitMethodCallExpression(expression);
      if (isSuperMethodCall(expression, myMethod)) {
        mySuperCallFound = true;
      }
    }

    @Override
    public void visitMethodReferenceExpression(PsiMethodReferenceExpression expression) {
      if (mySuperCallFound) {
        return;
      }
      final PsiExpression qualifier = expression.getQualifierExpression();
      if (qualifier instanceof PsiSuperExpression) {
        final PsiElement target = expression.resolve();
        if (target instanceof PsiMethod) {
          if (MethodSignatureUtil.isSuperMethod((PsiMethod)target, myMethod)) {
            mySuperCallFound = true;
            return;
          }
        }
      }
      super.visitMethodReferenceExpression(expression);
    }

    boolean isSuperCallFound() {
      return mySuperCallFound;
    }
  }
}