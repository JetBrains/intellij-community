/*
 * Copyright 2008-2016 Bas Leijdekkers
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
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class JUnit4AnnotatedMethodInJUnit3TestCaseInspection extends JUnit4AnnotatedMethodInJUnit3TestCaseInspectionBase {

  @NotNull
  @Override
  protected InspectionGadgetsFix[] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList<>(3);
    final PsiMethod method = (PsiMethod)infos[1];
    if (AnnotationUtil.isAnnotated(method, IGNORE, 0)) {
      fixes.add(new RemoveIgnoreAndRename(method));
    }
    if (TestUtils.isJUnit4TestMethod(method)) {
      String methodName = method.getName();
      String newMethodName;
      if (methodName.startsWith("test")) {
        newMethodName = null;
      }
      else {
        boolean lowCaseStyle = methodName.contains("_");
        newMethodName = "test" + (lowCaseStyle ? "_" + methodName : StringUtil.capitalize(methodName));
      }
      fixes.add(new RemoveTestAnnotationFix(newMethodName));
    }
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    fixes.add(new ConvertToJUnit4Fix(className));
    return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
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
      return InspectionGadgetsBundle.message("ignore.test.method.in.class.extending.junit3.testcase.quickfix", getTargetName());
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      WriteAction.run(() -> deleteAnnotation(descriptor, IGNORE));
      super.doFix(project, descriptor);
    }
  }

  private static class ConvertToJUnit4Fix extends InspectionGadgetsFix {

    private final String className;

    ConvertToJUnit4Fix(String className) {
      this.className = className;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("convert.junit3.test.class.quickfix", className);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Convert JUnit 3 class to JUnit 4";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMember)) {
        return;
      }
      final PsiMember member = (PsiMember)parent;
      final PsiClass containingClass = member.getContainingClass();
      convertJUnit3ClassToJUnit4(containingClass);
    }
  }

  public static void convertJUnit3ClassToJUnit4(PsiClass containingClass) {
    if (containingClass == null) {
      return;
    }
    final PsiReferenceList extendsList = containingClass.getExtendsList();
    if (extendsList == null) {
      return;
    }
    for (PsiMethod method : containingClass.getMethods()) {
      @NonNls final String name = method.getName();
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          PsiType.VOID.equals(method.getReturnType()) &&
          method.getParameterList().getParametersCount() == 0) {
        final PsiModifierList modifierList = method.getModifierList();
        if (name.startsWith("test")) {
          addAnnotationIfNotPresent(modifierList, "org.junit.Test");
        }
        else if (name.equals("setUp")) {
          transformSetUpOrTearDownMethod(method);
          addAnnotationIfNotPresent(modifierList, "org.junit.Before");
        }
        else if (name.equals("tearDown")) {
          transformSetUpOrTearDownMethod(method);
          addAnnotationIfNotPresent(modifierList, "org.junit.After");
        }
      }
      method.accept(new MethodCallModifier());
    }
    final PsiJavaCodeReferenceElement[] referenceElements = extendsList.getReferenceElements();
    for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
      referenceElement.delete();
    }
  }

  private static void addAnnotationIfNotPresent(PsiModifierList modifierList, String qualifiedAnnotationName) {
    if (modifierList.findAnnotation(qualifiedAnnotationName) != null) {
      return;
    }
    final PsiAnnotation annotation = modifierList.addAnnotation(qualifiedAnnotationName);
    final Project project = modifierList.getProject();
    final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    codeStyleManager.shortenClassReferences(annotation);
  }

  private static void transformSetUpOrTearDownMethod(PsiMethod method) {
    final PsiModifierList modifierList = method.getModifierList();
    if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
      modifierList.setModifierProperty(PsiModifier.PROTECTED, false);
    }
    if (!modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
      modifierList.setModifierProperty(PsiModifier.PUBLIC, true);
    }
    final PsiAnnotation overrideAnnotation = modifierList.findAnnotation("java.lang.Override");
    if (overrideAnnotation != null) {
      overrideAnnotation.delete();
    }
    method.accept(new SuperLifeCycleCallRemover(method.getName()));
  }

  private static class SuperLifeCycleCallRemover extends JavaRecursiveElementVisitor {

    @NotNull private final String myLifeCycleMethodName;

    private SuperLifeCycleCallRemover(@NotNull String lifeCycleMethodName) {
      myLifeCycleMethodName = lifeCycleMethodName;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      if (!myLifeCycleMethodName.equals(methodName)) {
        return;
      }
      final PsiExpression target = methodExpression.getQualifierExpression();
      if (!(target instanceof PsiSuperExpression)) {
        return;
      }
      expression.delete();
    }
  }

  private static class MethodCallModifier extends JavaRecursiveElementVisitor {

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      if (methodExpression.getQualifierExpression() != null) {
        return;
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null || !method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      final PsiClass aClass = method.getContainingClass();
      if (aClass == null) {
        return;
      }
      final String name = aClass.getQualifiedName();
      if (!"junit.framework.Assert".equals(name) && !"junit.framework.TestCase".equals(name)) {
        return;
      }
      @NonNls final String newExpressionText = "org.junit.Assert." + expression.getText();
      final Project project = expression.getProject();
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiExpression newExpression = factory.createExpressionFromText(newExpressionText, expression);
      final JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
      final PsiElement replacedExpression = expression.replace(newExpression);
      codeStyleManager.shortenClassReferences(replacedExpression);
    }
  }

  private static class RemoveTestAnnotationFix extends RenameFix {
    private final String myNewName;

    public RemoveTestAnnotationFix(String newName) {
      super(newName);
      myNewName = newName;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.junit4.test.annotation.quickfix");
    }

    @Override
    @NotNull
    public String getName() {
      return myNewName == null ? getFamilyName()
                               : InspectionGadgetsBundle.message("remove.junit4.test.annotation.and.rename.quickfix", myNewName);
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    public void doFix(Project project, ProblemDescriptor descriptor) {
      WriteAction.run(() -> deleteAnnotation(descriptor, "org.junit.Test"));
      if (myNewName != null) {
        super.doFix(project, descriptor);
      }
    }
  }
}
