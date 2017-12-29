/*
 * Copyright 2003-2017 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringChangeUtil;
import com.siyeh.HardcodedMethodConstants;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.util.ObjectUtils.tryCast;

public class MethodCallUtils {

  /**
   * @noinspection StaticCollection
   */
  @NonNls private static final Set<String> regexMethodNames = new HashSet<>(5);

  static {
    regexMethodNames.add("compile");
    regexMethodNames.add("matches");
    regexMethodNames.add("replaceFirst");
    regexMethodNames.add("replaceAll");
    regexMethodNames.add("split");
  }

  private MethodCallUtils() {}

  @Nullable
  public static String getMethodName(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression method = expression.getMethodExpression();
    return method.getReferenceName();
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

  public static boolean isCompareToCall(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (!HardcodedMethodConstants.COMPARE_TO.equals(methodExpression.getReferenceName())) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    return MethodUtils.isCompareTo(method);
  }

  public static boolean isCompareToIgnoreCaseCall(@NotNull PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (!"compareToIgnoreCase".equals(methodExpression.getReferenceName())) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    return MethodUtils.isCompareToIgnoreCase(method);
  }

  public static boolean isEqualsCall(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.EQUALS.equals(name)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    return MethodUtils.isEquals(method);
  }

  public static boolean isEqualsIgnoreCaseCall(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!HardcodedMethodConstants.EQUALS_IGNORE_CASE.equals(name)) {
      return false;
    }
    final PsiMethod method = expression.resolveMethod();
    return MethodUtils.isEqualsIgnoreCase(method);
  }

  public static boolean isSimpleCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, @NonNls @Nullable String... parameterTypeStrings) {
    if (parameterTypeStrings == null) {
      return isCallToMethod(expression, calledOnClassName, returnType, methodName, (PsiType[])null);
    }
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

  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @Nullable Pattern methodNamePattern, @Nullable PsiType... parameterTypes) {
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

  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, @Nullable PsiType... parameterTypes) {
    if (methodName != null) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String referenceName = methodExpression.getReferenceName();
      if (!methodName.equals(referenceName)) {
        return false;
      }
    }
    final PsiMethod method = expression.resolveMethod();
    if (method == null) {
      return false;
    }
    return MethodUtils.methodMatches(method, calledOnClassName, returnType, methodName, parameterTypes);
  }

  public static boolean isCallToRegexMethod(PsiMethodCallExpression expression) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String name = methodExpression.getReferenceName();
    if (!regexMethodNames.contains(name)) {
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

  public static boolean isCallDuringObjectConstruction(PsiMethodCallExpression expression) {
    final PsiMember member = PsiTreeUtil.getParentOfType(expression, PsiMember.class, true, PsiClass.class, PsiLambdaExpression.class);
    if (member == null) {
      return false;
    }
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (qualifier != null) {
      if (!(qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression)) {
        return false;
      }
    }
    final PsiClass containingClass = member.getContainingClass();
    if (containingClass == null || containingClass.hasModifierProperty(PsiModifier.FINAL)) {
      return false;
    }
    if (member instanceof PsiClassInitializer) {
      final PsiClassInitializer classInitializer = (PsiClassInitializer)member;
      if (!classInitializer.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    else if (member instanceof PsiMethod) {
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
    else if (member instanceof PsiField) {
      final PsiField field = (PsiField)member;
      if (!field.hasModifierProperty(PsiModifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  public static boolean isMethodCallOnVariable(@NotNull PsiMethodCallExpression expression,
                                               @NotNull PsiVariable variable,
                                               @NotNull String methodName) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    @NonNls final String name = methodExpression.getReferenceName();
    if (!methodName.equals(name)) {
      return false;
    }
    final PsiExpression qualifier = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
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

  public static boolean isSuperMethodCall(@NotNull PsiMethodCallExpression expression, @NotNull PsiMethod method) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression target = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
    if (!(target instanceof PsiSuperExpression)) {
      return false;
    }
    final PsiMethod targetMethod = expression.resolveMethod();
    return targetMethod != null && MethodSignatureUtil.isSuperMethod(targetMethod, method);
  }

  /**
   * Returns true if given method call is a var-arg call
   *
   * @param call a call to test
   * @return true if call is resolved to the var-arg method and var-arg form is actually used
   */
  public static boolean isVarArgCall(PsiMethodCallExpression call) {
    JavaResolveResult result = call.resolveMethodGenerics();
    PsiMethod method = tryCast(result.getElement(), PsiMethod.class);
    if(method == null || !method.isVarArgs()) return false;
    PsiSubstitutor substitutor = result.getSubstitutor();
    return MethodCallInstruction
      .isVarArgCall(method, substitutor, call.getArgumentList().getExpressions(), method.getParameterList().getParameters());
  }

  public static boolean containsSuperMethodCall(@NotNull PsiMethod method) {
    final SuperCallVisitor visitor = new SuperCallVisitor(method);
    method.accept(visitor);
    return visitor.isSuperCallFound();
  }

  public static boolean callWithNonConstantString(@NotNull PsiMethodCallExpression expression, boolean considerStaticFinalConstant,
                                                  String className, String... methodNames) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final String methodName = methodExpression.getReferenceName();
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
    final PsiExpression argument = ParenthesesUtils.stripParentheses(ExpressionUtils.getFirstExpressionInList(argumentList));
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
  public static PsiMethodCallExpression getQualifierMethodCall(@NotNull PsiMethodCallExpression methodCall) {
    return
      tryCast(PsiUtil.skipParenthesizedExprDown(methodCall.getMethodExpression().getQualifierExpression()), PsiMethodCallExpression.class);
  }

  public static boolean isUsedAsSuperConstructorCallArgument(@NotNull PsiParameter parameter, boolean superMustBeLibrary) {
    final PsiElement scope = parameter.getDeclarationScope();
    if (!(scope instanceof PsiMethod)) {
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
      final PsiMethodCallExpression call = MethodUtils.findSuperOrThisCall(method);
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
      if (RefactoringChangeUtil.isSuperMethodCall(call) && (!superMustBeLibrary || method instanceof PsiCompiledElement)) {
        return true;
      }
      parameter = method.getParameterList().getParameters()[index];
    }
  }

  private static int getParameterReferenceIndex(PsiMethodCallExpression call, PsiParameter parameter) {
    final PsiExpressionList argumentList = call.getArgumentList();
    final PsiExpression[] arguments = argumentList.getExpressions();
    for (int i = 0; i < arguments.length; i++) {
      PsiExpression argument = arguments[i];
      argument = ParenthesesUtils.stripParentheses(argument);
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

    public SuperCallVisitor(@NotNull PsiMethod method) {
      this.myMethod = method;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
      if (!mySuperCallFound) {
        super.visitElement(element);
      }
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // anonymous and inner classes inside methods are visited to reduce false positives
      super.visitClass(aClass);
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      // lambda's are visited to reduce false positives
      super.visitLambdaExpression(expression);
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