/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.dataFlow.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.MethodContract;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public class SideEffectChecker {
  private static final Set<String> ourSideEffectFreeClasses = new THashSet<>(Arrays.asList(
    Object.class.getName(),
    Short.class.getName(),
    Character.class.getName(),
    Byte.class.getName(),
    Integer.class.getName(),
    Long.class.getName(),
    Float.class.getName(),
    Double.class.getName(),
    String.class.getName(),
    StringBuffer.class.getName(),
    Boolean.class.getName(),

    ArrayList.class.getName(),
    Date.class.getName(),
    HashMap.class.getName(),
    HashSet.class.getName(),
    Hashtable.class.getName(),
    LinkedHashMap.class.getName(),
    LinkedHashSet.class.getName(),
    LinkedList.class.getName(),
    Stack.class.getName(),
    TreeMap.class.getName(),
    TreeSet.class.getName(),
    Vector.class.getName(),
    WeakHashMap.class.getName()));

  private SideEffectChecker() {
  }

  public static boolean mayHaveSideEffects(@NotNull PsiExpression exp) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null, exp);
    exp.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static boolean mayHaveSideEffects(@NotNull PsiElement element, Predicate<PsiElement> shouldIgnoreElement) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null, element, shouldIgnoreElement);
    element.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static boolean checkSideEffects(@NotNull PsiExpression element, @NotNull List<PsiElement> sideEffects) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(sideEffects, element);
    element.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static List<PsiExpression> extractSideEffectExpressions(@NotNull PsiExpression element) {
    List<PsiElement> list = new SmartList<>();
    element.accept(new SideEffectsVisitor(list, element));
    return StreamEx.of(list).select(PsiExpression.class).toList();
  }

  private static class SideEffectsVisitor extends JavaRecursiveElementWalkingVisitor {
    private final @Nullable List<PsiElement> mySideEffects;
    private final @NotNull PsiElement myStartElement;
    private final @NotNull Predicate<PsiElement> myIgnorePredicate;
    boolean found;

    SideEffectsVisitor(@Nullable List<PsiElement> sideEffects, @NotNull PsiElement startElement) {
      this(sideEffects, startElement, call -> false);
    }

    SideEffectsVisitor(@Nullable List<PsiElement> sideEffects, @NotNull PsiElement startElement, @NotNull Predicate<PsiElement> predicate) {
      myStartElement = startElement;
      myIgnorePredicate = predicate;
      mySideEffects = sideEffects;
    }

    private boolean addSideEffect(PsiElement element) {
      if (myIgnorePredicate.test(element)) return false;
      found = true;
      if(mySideEffects != null) {
        mySideEffects.add(element);
      } else {
        stopWalking();
      }
      return true;
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      if (addSideEffect(expression)) return;
      super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiMethod method = expression.resolveMethod();
      if (!isPure(method)) {
        if (addSideEffect(expression)) return;
      }
      super.visitMethodCallExpression(expression);
    }

    protected boolean isPure(PsiMethod method) {
      if (method == null) return false;
      if (PropertyUtil.isSimpleGetter(method)) return true;
      return ControlFlowAnalyzer.isPure(method) && !mayHaveExceptionalSideEffect(method);
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      if(!isSideEffectFreeConstructor(expression)) {
        if (addSideEffect(expression)) return;
      }
      super.visitNewExpression(expression);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS)) {
        if (addSideEffect(expression)) return;
      }
      super.visitUnaryExpression(expression);
    }

    @Override
    public void visitVariable(PsiVariable variable) {
      if (addSideEffect(variable)) return;
      super.visitVariable(variable);
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      PsiStatement exitedStatement = statement.findExitedStatement();
      if (exitedStatement != null && PsiTreeUtil.isAncestor(myStartElement, exitedStatement, true)) return;
      if (addSideEffect(statement)) return;
      super.visitBreakStatement(statement);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // local or anonymous class declaration is not side effect per se (unless it's instantiated)
    }

    @Override
    public void visitContinueStatement(PsiContinueStatement statement) {
      PsiStatement exitedStatement = statement.findContinuedStatement();
      if (exitedStatement != null && PsiTreeUtil.isAncestor(myStartElement, exitedStatement, false)) return;
      if (addSideEffect(statement)) return;
      super.visitContinueStatement(statement);
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      if (addSideEffect(statement)) return;
      super.visitReturnStatement(statement);
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      if (addSideEffect(statement)) return;
      super.visitThrowStatement(statement);
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      // lambda is not side effect per se (unless it's called)
    }

    public boolean mayHaveSideEffects() {
      return found;
    }
  }

  /**
   * Returns true if given method function is likely to throw an exception (e.g. "assertEquals"). In some cases this means that
   * the method call should be preserved in source code even if it's pure (i.e. does not change the program state).
   *
   * @param method a method to check
   * @return true if the method has exceptional side effect
   */
  public static boolean mayHaveExceptionalSideEffect(PsiMethod method) {
    if (method.getName().startsWith("assert") || method.getName().startsWith("check")) {
      return true;
    }
    return ControlFlowAnalyzer.getMethodCallContracts(method, null).stream()
      .filter(mc -> mc.getConditions().stream().noneMatch(cv -> cv.isBoundCheckingCondition()))
      .anyMatch(mc -> mc.getReturnValue() == MethodContract.ValueConstraint.THROW_EXCEPTION);
  }

  private static boolean isSideEffectFreeConstructor(@NotNull PsiNewExpression newExpression) {
    PsiJavaCodeReferenceElement classReference = newExpression.getClassReference();
    PsiClass aClass = classReference == null ? null : (PsiClass)classReference.resolve();
    String qualifiedName = aClass == null ? null : aClass.getQualifiedName();
    if (qualifiedName == null) return false;
    if (ourSideEffectFreeClasses.contains(qualifiedName)) return true;

    PsiFile file = aClass.getContainingFile();
    PsiDirectory directory = file.getContainingDirectory();
    PsiPackage classPackage = directory == null ? null : JavaDirectoryService.getInstance().getPackage(directory);
    String packageName = classPackage == null ? null : classPackage.getQualifiedName();

    // all Throwable descendants from java.lang are side effects free
    if (CommonClassNames.DEFAULT_PACKAGE.equals(packageName) || "java.io".equals(packageName)) {
      PsiClass throwableClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass("java.lang.Throwable", aClass.getResolveScope());
      if (throwableClass != null && com.intellij.psi.util.InheritanceUtil.isInheritorOrSelf(aClass, throwableClass, true)) {
        return true;
      }
    }
    return false;
  }
}
