/*
 * Copyright 2008-2009 Bas Leijdekkers
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
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class JUnit4AnnotatedMethodInJUnit3TestCaseInspection extends
                                                             BaseInspection {

  private static final String IGNORE = "org.junit.Ignore";

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "junit4.test.method.in.class.extending.junit3.testcase.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {

    if (infos[0] instanceof PsiMethod &&
        AnnotationUtil.isAnnotated((PsiModifierListOwner)infos[0], IGNORE, false)) {
      return InspectionGadgetsBundle.message("ignore.test.method.in.class.extending.junit3.testcase.problem.descriptor");
    }
    return InspectionGadgetsBundle.message(
      "junit4.test.method.in.class.extending.junit3.testcase.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    String className = null;
    if (infos[0] instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod)infos[0];
      PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) return InspectionGadgetsFix.EMPTY_ARRAY;
      className = containingClass.getName();
      if (AnnotationUtil.isAnnotated(method, IGNORE, false)) {
        if (!TestUtils.isJUnit4TestMethod(method)) {
          return new InspectionGadgetsFix[]{new RemoveIgnoreAndRename(method),
            new RemoveExtendsTestCaseFix(className)};
        }
        else {
          return new InspectionGadgetsFix[]{new RemoveIgnoreAndRename(method),
            new RemoveTestAnnotationFix(),
            new RemoveExtendsTestCaseFix(className)};
        }
      }
    }

    if (className == null) {
      className = (String)infos[0];
    }
    if (className != null) {
      return new InspectionGadgetsFix[]{
        new RemoveTestAnnotationFix(),
        new RemoveExtendsTestCaseFix(className)
      };
    }
    else {
      return new InspectionGadgetsFix[]{
        new RemoveTestAnnotationFix()
      };
    }
  }

  private static void deleteAnnotation(ProblemDescriptor descriptor, final String qualifiedName) {
    final PsiElement element = descriptor.getPsiElement();
    final PsiElement parent = element.getParent();
    if (!(parent instanceof PsiModifierListOwner)) {
      return;
    }
    final PsiModifierListOwner method = (PsiModifierListOwner)parent;
    final PsiModifierList modifierList = method.getModifierList();
    if (modifierList == null) {
      return;
    }
    final PsiAnnotation annotation = modifierList.findAnnotation(qualifiedName);
    if (annotation == null) {
      return;
    }
    annotation.delete();
  }

  private static class RemoveIgnoreAndRename extends RenameFix {
    public RemoveIgnoreAndRename(@NonNls PsiMethod method) {
      super("_" + method.getName());
    }

    @NotNull
    @Override
    public String getName() {
      return InspectionGadgetsBundle.message("ignore.test.method.in.class.extending.junit3.testcase.problem.fix", getTargetName());
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      deleteAnnotation(descriptor, IGNORE);
      super.doFix(project, descriptor);
    }
  }


  private static class RemoveExtendsTestCaseFix extends InspectionGadgetsFix {
    private final String className;

    RemoveExtendsTestCaseFix(String className) {
      this.className = className;
    }

    @NotNull
    public String getName() {
      return "remove 'extends TestCase' from class '" + className + '\'';
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMember)) {
        return;
      }
      final PsiMember method = (PsiMember)parent;
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiReferenceList extendsList =
        containingClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      extendsList.delete();
    }
  }


  private static class RemoveTestAnnotationFix
    extends InspectionGadgetsFix {

    @NotNull
    public String getName() {
      return "Remove @Test annotation";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      deleteAnnotation(descriptor, "org.junit.Test");
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Junit4AnnotatedMethodInJunit3TestCaseVisitor();
  }

  private static class Junit4AnnotatedMethodInJunit3TestCaseVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!TestUtils.isJUnitTestClass(containingClass)) {
        return;
      }
      if (AnnotationUtil.isAnnotated(method, IGNORE, false) && method.getName().startsWith("test")) {
        registerMethodError(method, method);
        return;
      }
      if (!TestUtils.isJUnit4TestMethod(method)) {
        return;
      }
      final String className = containingClass.getName();
      registerMethodError(method, className);
    }
  }
}
