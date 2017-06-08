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
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null);
    exp.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static boolean mayHaveSideEffects(@NotNull PsiElement element, Predicate<PsiMethodCallExpression> shouldIgnoreCall) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(null, shouldIgnoreCall);
    element.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static boolean checkSideEffects(@NotNull PsiExpression element, @NotNull List<PsiElement> sideEffects) {
    final SideEffectsVisitor visitor = new SideEffectsVisitor(sideEffects);
    element.accept(visitor);
    return visitor.mayHaveSideEffects();
  }

  public static List<PsiExpression> extractSideEffectExpressions(@NotNull PsiExpression element) {
    List<PsiElement> list = new ArrayList<>();
    element.accept(new SideEffectsVisitor(list));
    return StreamEx.of(list).select(PsiExpression.class).toList();
  }

  private static class SideEffectsVisitor extends JavaRecursiveElementWalkingVisitor {
    private @Nullable final List<PsiElement> mySideEffects;
    boolean found;
    final Predicate<PsiMethodCallExpression> myIgnoredCallPredicate;

    SideEffectsVisitor(@Nullable List<PsiElement> sideEffects) {
      this(sideEffects, call -> false);
    }

    SideEffectsVisitor(@Nullable List<PsiElement> sideEffects, Predicate<PsiMethodCallExpression> predicate) {
      myIgnoredCallPredicate = predicate;
      mySideEffects = sideEffects;
    }

    private void addSideEffect(PsiElement element) {
      found = true;
      if(mySideEffects != null) {
        mySideEffects.add(element);
      } else {
        stopWalking();
      }
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      addSideEffect(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (!myIgnoredCallPredicate.test(expression)) {
        final PsiMethod method = expression.resolveMethod();
        if (!isPure(method)) {
          addSideEffect(expression);
          return;
        }
      }
      super.visitMethodCallExpression(expression);
    }

    protected boolean isPure(PsiMethod method) {
      if (method == null) return false;
      if (PropertyUtil.isSimpleGetter(method)) return true;
      if (ControlFlowAnalyzer.isPure(method)) {
        return ControlFlowAnalyzer.getMethodContracts(method).stream()
          .noneMatch(mc -> mc.returnValue == MethodContract.ValueConstraint.THROW_EXCEPTION);
      }
      return false;
    }

    @Override
    public void visitNewExpression(@NotNull PsiNewExpression expression) {
      if(!isSideEffectFreeConstructor(expression)) {
        addSideEffect(expression);
        return;
      }
      super.visitNewExpression(expression);
    }

    @Override
    public void visitPostfixExpression(@NotNull PsiPostfixExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS)) {
        addSideEffect(expression);
        return;
      }
      super.visitPostfixExpression(expression);
    }

    @Override
    public void visitPrefixExpression(@NotNull PsiPrefixExpression expression) {
      final IElementType tokenType = expression.getOperationTokenType();
      if (tokenType.equals(JavaTokenType.PLUSPLUS) || tokenType.equals(JavaTokenType.MINUSMINUS)) {
        addSideEffect(expression);
        return;
      }
      super.visitPrefixExpression(expression);
    }

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
      addSideEffect(statement);
    }

    @Override
    public void visitBreakStatement(PsiBreakStatement statement) {
      addSideEffect(statement);
    }

    @Override
    public void visitClass(PsiClass aClass) {
      // local or anonymous class declaration is not side effect per se (unless it's instantiated)
    }

    @Override
    public void visitContinueStatement(PsiContinueStatement statement) {
      addSideEffect(statement);
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
      addSideEffect(statement);
    }

    @Override
    public void visitThrowStatement(PsiThrowStatement statement) {
      addSideEffect(statement);
    }

    @Override
    public void visitLambdaExpression(PsiLambdaExpression expression) {
      // lambda is not side effect per se (unless it's called)
    }

    public boolean mayHaveSideEffects() {
      return found;
    }
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
