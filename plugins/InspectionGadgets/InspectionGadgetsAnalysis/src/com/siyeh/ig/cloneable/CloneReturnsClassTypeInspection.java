// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * @author Bas Leijdekkers
 */
public class CloneReturnsClassTypeInspection extends BaseInspection {

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("clone.returns.class.type.problem.descriptor", infos[0]);
  }

  @Nullable
  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String className = (String)infos[0];
    final boolean buildFix = ((Boolean)infos[1]).booleanValue();
    if (!buildFix) {
      return null;
    }
    return new CloneReturnsClassTypeFix(className);
  }

  private static class CloneReturnsClassTypeFix extends InspectionGadgetsFix {

    final String myClassName;

    CloneReturnsClassTypeFix(String className) {
      myClassName = className;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("clone.returns.class.type.quickfix", myClassName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("clone.returns.class.type.family.quickfix");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement)) {
        return;
      }
      final PsiTypeElement typeElement = (PsiTypeElement)element;
      final PsiElement parent = typeElement.getParent();
      if (!(parent instanceof PsiMethod)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(myClassName, element);
      final PsiType newType = newTypeElement.getType();
      parent.accept(new JavaRecursiveElementVisitor() {

        @Override
        public void visitClass(@NotNull PsiClass aClass) {}

        @Override
        public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

        @Override
        public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
          super.visitReturnStatement(statement);
          final PsiExpression returnValue = PsiUtil.deparenthesizeExpression(statement.getReturnValue());
          if (returnValue == null) {
            return;
          }
          final PsiType type = returnValue.getType();
          if (newType.equals(type) || PsiType.NULL.equals(type)) {
            return;
          }
          final CommentTracker commentTracker = new CommentTracker();
          PsiReplacementUtil.replaceStatement(statement, "return (" + myClassName + ')' + commentTracker.text(returnValue) + ';',
                                              commentTracker);
        }
      });
      typeElement.getFirstChild().replace(newTypeElement.getFirstChild());
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneReturnsClassTypeVisitor();
  }

  private static class CloneReturnsClassTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      if (!CloneUtils.isClone(method) || !PsiUtil.isLanguageLevel5OrHigher(method)) {
        return;
      }
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiType returnType = typeElement.getType();
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(returnType);
      PsiClass containingClass = method.getContainingClass();
      if (containingClass instanceof PsiAnonymousClass) {
        final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)containingClass;
        final PsiClassType baseClassType = anonymousClass.getBaseClassType();
        containingClass = PsiUtil.resolveClassInClassTypeOnly(baseClassType);
      }
      if (containingClass == null || containingClass.equals(aClass)) {
        return;
      }
      if (methodAlwaysReturnsNullOrThrowsException(method)) {
        return;
      }
      if (!CloneUtils.isCloneable(containingClass)) {
        if (JavaPsiFacade.getElementFactory(method.getProject()).createType(containingClass).isConvertibleFrom(returnType)) {
          return;
        }
        registerError(typeElement, containingClass.getName(), Boolean.FALSE);
      }
      else {
        registerError(typeElement, containingClass.getName(), Boolean.TRUE);
      }
    }

    private static boolean methodAlwaysReturnsNullOrThrowsException(@NotNull PsiMethod method) {
      final PsiCodeBlock body = method.getBody();
      if (body == null) {
        return false;
      }
      final ReturnChecker checker = new ReturnChecker(r -> ExpressionUtils.isNullLiteral(r.getReturnValue()));
      body.accept(checker);
      return checker.isReturnFound() ? checker.allReturnsMatchPredicate() : !ControlFlowUtils.codeBlockMayCompleteNormally(body);
    }
  }

  private static class ReturnChecker extends JavaRecursiveElementWalkingVisitor {

    private final Predicate<? super PsiReturnStatement> myPredicate;

    private boolean myReturnFound = false;
    private boolean myallReturnsMatchPredicate = true;

    ReturnChecker(Predicate<? super PsiReturnStatement> predicate) {
      myPredicate = predicate;
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {}

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {}

    @Override
    public void visitThrowStatement(@NotNull PsiThrowStatement statement) {
      super.visitThrowStatement(statement);
      myallReturnsMatchPredicate = false;
      stopWalking();
    }

    @Override
    public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
      super.visitReturnStatement(statement);
      myReturnFound = true;
      myallReturnsMatchPredicate &= myPredicate.test(statement);
      if (!myallReturnsMatchPredicate) {
        stopWalking();
      }
    }

    public boolean allReturnsMatchPredicate() {
      return myallReturnsMatchPredicate;
    }

    public boolean isReturnFound() {
      return myReturnFound;
    }
  }
}
