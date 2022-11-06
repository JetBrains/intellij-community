// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.ExpressionUtil;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Query;
import com.intellij.util.containers.MultiMap;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RenameFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpectedTypeUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.codeInsight.AnnotationUtil.CHECK_HIERARCHY;

public class JUnit4AnnotatedMethodInJUnit3TestCaseInspection extends BaseInspection {

  protected static final String IGNORE = "org.junit.Ignore";

  @Override
  protected InspectionGadgetsFix @NotNull [] buildFixes(Object... infos) {
    final List<InspectionGadgetsFix> fixes = new ArrayList<>(3);
    final PsiMethod method = (PsiMethod)infos[1];
    if (AnnotationUtil.isAnnotated(method, IGNORE, 0)) {
      fixes.add(new RemoveIgnoreAndRenameFix(method));
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
    return fixes.toArray(InspectionGadgetsFix.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    if (AnnotationUtil.isAnnotated((PsiMethod)infos[1], IGNORE, 0)) {
      return InspectionGadgetsBundle.message("ignore.test.method.in.class.extending.junit3.testcase.problem.descriptor");
    }
    return InspectionGadgetsBundle.message("junit4.test.method.in.class.extending.junit3.testcase.problem.descriptor");
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new Junit4AnnotatedMethodInJunit3TestCaseVisitor();
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
    JavaCodeStyleManager.getInstance(element.getProject()).optimizeImports(element.getContainingFile());
  }

  private static class RemoveIgnoreAndRenameFix extends RenameFix {

    RemoveIgnoreAndRenameFix(@NonNls PsiMethod method) {
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
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      WriteAction.run(() -> deleteAnnotation(descriptor, IGNORE));
      super.doFix(project, descriptor);
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      deleteAnnotation(previewDescriptor, IGNORE);
      return super.generatePreview(project, previewDescriptor);
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
      return InspectionGadgetsBundle.message("convert.to.j.unit.4.fix.family.name");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      convertJUnit3ClassToJUnit4(getPsiClass(descriptor));
    }

    @Nullable
    private static PsiClass getPsiClass(ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      final PsiMember member = ObjectUtils.tryCast(element.getParent(), PsiMember.class);
      return member == null ? null : member.getContainingClass();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      PsiClass psiClass = getPsiClass(previewDescriptor);
      if (psiClass == null) {
        return IntentionPreviewInfo.EMPTY;
      }
      performConversion(psiClass);
      return IntentionPreviewInfo.DIFF;
    }
  }

  public static void convertJUnit3ClassToJUnit4(PsiClass junit3Class) {
    if (junit3Class == null) {
      return;
    }
    final MultiMap<PsiElement, String> conflicts = checkForConflicts(junit3Class);
    if (conflicts == null) return; // cancelled by user

    final Runnable runnable = () -> WriteAction.run(() -> performConversion(junit3Class));
    if (!conflicts.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        if (!BaseRefactoringProcessor.ConflictsInTestsException.isTestIgnore()) {
          throw new BaseRefactoringProcessor.ConflictsInTestsException(conflicts.values());
        }
      }
      else if (!new ConflictsDialog(junit3Class.getProject(), conflicts, runnable).showAndGet()) {
        return;
      }
    }
    runnable.run();
  }

  private static void performConversion(PsiClass junit3Class) {
    final PsiReferenceList extendsList = junit3Class.getExtendsList();
    if (extendsList == null) {
      return;
    }
    for (PsiMethod method : junit3Class.getMethods()) {
      @NonNls final String name = method.getName();
      if (!method.hasModifierProperty(PsiModifier.STATIC) &&
          PsiType.VOID.equals(method.getReturnType()) &&
          method.getParameterList().isEmpty()) {
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

  @Nullable
  private static MultiMap<PsiElement, String> checkForConflicts(PsiClass junit3Class) {
    final MultiMap<PsiElement, String> conflicts = new MultiMap<>();
    final String className = junit3Class.getQualifiedName();
    final PsiClass objectClass = ClassUtils.findObjectClass(junit3Class);
    junit3Class.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        final PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (!ExpressionUtil.isEffectivelyUnqualified(methodExpression)) {
          return;
        }
        final PsiMethod method = expression.resolveMethod();
        if (method == null || method.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
        final PsiClass aClass = method.getContainingClass();
        if (aClass == null || aClass == junit3Class || aClass == objectClass ||
            !junit3Class.isInheritor(aClass, true)) {
          return;
        }
        final String className = aClass.getQualifiedName();
        if ("junit.framework.Assert".equals(className) || "junit.framework.TestCase".equals(className)) {
          @NonNls final String methodName = method.getName();
          if ("setUp".equals(methodName) || "tearDown".equals(methodName)) {
            return;
          }
        }
        final PsiMethod[] superMethods = method.findSuperMethods(objectClass);
        final String expressionText = CommonRefactoringUtil.htmlEmphasize(expression.getText());
        final String classText = RefactoringUIUtil.getDescription(junit3Class, false);
        final @Nls String problem;
        if (superMethods.length > 0) {
          problem = InspectionGadgetsBundle.message("convert.junit3.test.fix.conflict.semantics", expressionText, classText);
        }
        else {
          problem = InspectionGadgetsBundle.message("convert.junit3.test.fix.conflict.compile", expressionText, classText);
        }
        conflicts.putValue(expression, problem);
      }
    });
    if (className != null) {
      final Query<PsiReference> search = ReferencesSearch.search(junit3Class, junit3Class.getUseScope());
      if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
        search.forEach(reference -> {
          final PsiElement element = reference.getElement().getParent();
          if (!(element instanceof PsiExpression)) {
            return true;
          }
          final PsiType expectedType = ExpectedTypeUtils.findExpectedType((PsiExpression)element, false);
          if (InheritanceUtil.isInheritor(expectedType, "junit.framework.Test") &&
              PsiUtil.resolveClassInClassTypeOnly(expectedType) != junit3Class) {
            final String elementText = CommonRefactoringUtil.htmlEmphasize(element.getText());
            final String classText = RefactoringUIUtil.getDescription(junit3Class, false);
            final @Nls String problem = InspectionGadgetsBundle.message("convert.junit3.test.fix.conflict.compile.2", elementText, classText);
            conflicts.putValue(element, problem);
          }
          return true;
        });
      }, RefactoringBundle.message("detecting.possible.conflicts"), true, junit3Class.getProject())) {
        return null;
      }
    }
    return conflicts;
  }

  private static void addAnnotationIfNotPresent(PsiModifierList modifierList, String qualifiedAnnotationName) {
    if (modifierList.hasAnnotation(qualifiedAnnotationName)) {
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

  private static final class SuperLifeCycleCallRemover extends JavaRecursiveElementVisitor {

    @NotNull private final String myLifeCycleMethodName;

    private SuperLifeCycleCallRemover(@NotNull String lifeCycleMethodName) {
      myLifeCycleMethodName = lifeCycleMethodName;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
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
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier != null && !(qualifier instanceof PsiSuperExpression)) {
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
      @NonNls final String newExpressionText = "org.junit.Assert." + methodExpression.getReferenceName() +
                                               expression.getArgumentList().getText();
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

    RemoveTestAnnotationFix(String newName) {
      super(newName);
      myNewName = newName;
    }

    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("remove.junit4.test.annotation.quickfix");
    }

    @Override
    public @NotNull String getName() {
      return myNewName == null ? getFamilyName()
                               : InspectionGadgetsBundle.message("remove.junit4.test.annotation.and.rename.quickfix", myNewName);
    }

    @Nullable
    @Override
    public PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
      return currentFile;
    }

    @Override
    public void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      WriteAction.run(() -> deleteAnnotation(descriptor, "org.junit.Test"));
      if (myNewName != null) {
        super.doFix(project, descriptor);
      }
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      deleteAnnotation(previewDescriptor, "org.junit.Test");
      if (myNewName == null) {
        return IntentionPreviewInfo.DIFF;
      }
      return super.generatePreview(project, previewDescriptor);
    }
  }

  private static class Junit4AnnotatedMethodInJunit3TestCaseVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!TestUtils.isJUnitTestClass(containingClass)) {
        return;
      }

      if (AnnotationUtil.isAnnotated(containingClass, TestUtils.RUN_WITH, CHECK_HIERARCHY)) {
        return;
      }

      if (AnnotationUtil.isAnnotated(method, IGNORE, 0) && method.getName().startsWith("test") ||
          TestUtils.isJUnit4TestMethod(method)) {
        registerMethodError(method, containingClass, method);
      }
    }
  }
}
