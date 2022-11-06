// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.CloneUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class RemoveCloneableFix extends InspectionGadgetsFix {

  private RemoveCloneableFix() {}

  /**
   * Factory method.
   * If the context is not null, it's children are checked for calls to clone() on this or super.
   * Such calls will start to throw a CloneNotSupportedException if Cloneable is removed from the implements clause,
   * so in this case no fix will be created.
   */
  public static RemoveCloneableFix create(@Nullable PsiElement context) {
    return isCloneCalledInChildren(context) ? null : new RemoveCloneableFix();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("remove.cloneable.quickfix");
  }

  @Override
  protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement().getParent();
    if (!(element instanceof PsiClass)) {
      return;
    }
    final PsiClass aClass = (PsiClass)element;
    final PsiReferenceList implementsList = aClass.getImplementsList();
    if (implementsList == null) {
      return;
    }
    final PsiClass cloneableClass = ClassUtils.findClass(CommonClassNames.JAVA_LANG_CLONEABLE, element);
    if (cloneableClass == null) {
      return;
    }
    final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      final PsiElement target = referenceElement.resolve();
      if (cloneableClass.equals(target)) {
        referenceElement.delete();
        return;
      }
    }
  }

  public static boolean isCloneCalledInChildren(PsiElement element) {
    if (element == null)  return false;
    final CloneCalledVisitor visitor = new CloneCalledVisitor();
    element.acceptChildren(visitor);
    return visitor.isCloneCalled();
  }

  private static class CloneCalledVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean cloneCalled = false;

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      if (cloneCalled) return;
      super.visitMethodCallExpression(expression);
      if (!CloneUtils.isCallToClone(expression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null || qualifier instanceof PsiThisExpression || qualifier instanceof PsiSuperExpression) {
        cloneCalled = true;
        stopWalking();
      }
    }

    @Override
    public void visitClass(@NotNull PsiClass aClass) {}

    private boolean isCloneCalled() {
      return cloneCalled;
    }
  }
}
