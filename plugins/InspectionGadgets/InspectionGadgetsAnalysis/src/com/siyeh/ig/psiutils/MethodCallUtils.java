/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignatureUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    final PsiReferenceExpression method = expression.getMethodExpression();
    final PsiExpression qualifierExpression = method.getQualifierExpression();
    if (qualifierExpression == null) {
      return null;
    }
    return qualifierExpression.getType();
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

  public static boolean isSimpleCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @NonNls @Nullable String methodName, @NonNls @Nullable String... parameterTypeStrings) {
    if (parameterTypeStrings == null) {
      return isCallToMethod(expression, calledOnClassName, returnType, methodName, null);
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

  public static boolean isCallToMethod(@NotNull PsiMethodCallExpression expression, @NonNls @Nullable String calledOnClassName,
    @Nullable PsiType returnType, @Nullable Pattern methodNamePattern, @Nullable PsiType... parameterTypes) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (methodNamePattern != null) {
      final String referenceName = methodExpression.getReferenceName();
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
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    if (methodName != null) {
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
    return copy.resolveMethod();
  }

  public static boolean isSuperMethodCall(@NotNull PsiMethodCallExpression expression, @NotNull PsiMethod method) {
    final PsiReferenceExpression methodExpression = expression.getMethodExpression();
    final PsiExpression target = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
    if (!(target instanceof PsiSuperExpression)) {
      return false;
    }
    final PsiMethod targetMethod = expression.resolveMethod();
    return targetMethod != null && MethodSignatureUtil.areSignaturesEqual(targetMethod, method);
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
    public void visitClass(PsiClass aClass) {}

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {}

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

    boolean isSuperCallFound() {
      return mySuperCallFound;
    }
  }
}