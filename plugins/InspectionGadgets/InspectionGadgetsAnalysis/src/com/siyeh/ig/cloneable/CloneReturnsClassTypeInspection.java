/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CloneUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class CloneReturnsClassTypeInspection extends BaseInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("clone.returns.class.type.display.name");
  }

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

    public CloneReturnsClassTypeFix(String className) {
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
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (!(element instanceof PsiTypeElement)) {
        return;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethod)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(myClassName, element);
      final PsiType newType = newTypeElement.getType();
      parent.accept(new JavaRecursiveElementVisitor() {
        @Override
        public void visitReturnStatement(PsiReturnStatement statement) {
          super.visitReturnStatement(statement);
          final PsiElement owner = PsiTreeUtil.getParentOfType(statement, PsiClass.class, PsiLambdaExpression.class, PsiMethod.class);
          if (owner != parent) {
            return;
          }
          final PsiExpression returnValue = PsiUtil.deparenthesizeExpression(statement.getReturnValue());
          if (returnValue == null || newType.equals(returnValue.getType())) {
            return;
          }
          CommentTracker commentTracker = new CommentTracker();
          PsiReplacementUtil.replaceStatement(statement, "return (" + myClassName + ')' + commentTracker.text(returnValue) + ';', commentTracker);
        }
      });
      element.replace(newTypeElement);

    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new CloneReturnsClassTypeVisitor();
  }

  private static class CloneReturnsClassTypeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      if (!CloneUtils.isClone(method) || !PsiUtil.isLanguageLevel5OrHigher(method)) {
        return;
      }
      final PsiTypeElement typeElement = method.getReturnTypeElement();
      if (typeElement == null) {
        return;
      }
      final PsiType returnType = typeElement.getType();
      final PsiClass aClass = PsiUtil.resolveClassInClassTypeOnly(returnType);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || containingClass.equals(aClass)) {
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
  }
}
