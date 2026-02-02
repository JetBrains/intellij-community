// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.execution.JUnitBundle;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiNameValuePair;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_AFTERSUITE;
import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_BEFORESUITE;
import static com.siyeh.ig.junit.JUnitCommonClassNames.ORG_JUNIT_PLATFORM_SUITE_API_SUITE;

public class JUnit5Framework extends JUnitTestFramework {

  @Override
  public boolean isDumbAware() {
    // Only Java is available in dumb mode, other language implementation might not support it.
    // For example, Kotlin, because it relies on light classes which require resolve.
    return this.getClass().isAssignableFrom(JUnit5Framework.class);
  }

  @Override
  public @NotNull String getName() {
    return "JUnit5";
  }

  @Override
  protected Collection<String> getMarkerClassFQNames() {
    return List.of(JUnitUtil.TEST5_ANNOTATION, JUnitUtil.CUSTOM_TESTABLE_ANNOTATION);
  }

  @Override
  protected String getMarkerClassFQName() {
    return JUnitUtil.TEST5_ANNOTATION;
  }

  @Override
  public boolean shouldRunSingleClassAsJUnit5(Project project, GlobalSearchScope scope) {
    return true;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT5;
  }

  @Override
  public @Nullable String getDefaultSuperClass() {
    return null;
  }

  @Override
  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (canBePotential) return isUnderTestSources(clazz);
      if (!isFrameworkAvailable(clazz)) return false;
      return JUnitUtil.isJUnit5TestClass(clazz, false);
    }, false);
  }

  @Override
  protected @Nullable PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    return findMethod(clazz, null, JUnitUtil.BEFORE_EACH_ANNOTATION_NAME);
  }

  @Override
  protected @Nullable PsiMethod findBeforeClassMethod(@NotNull PsiClass clazz) {
    return findMethod(clazz, PsiModifier.STATIC, JUnitUtil.BEFORE_ALL_ANNOTATION_NAME);
  }

  @Override
  protected @Nullable PsiElement findBeforeSuiteMethod(@NotNull PsiClass clazz) {
    return findMethod(clazz, PsiModifier.STATIC, ORG_JUNIT_PLATFORM_SUITE_API_BEFORESUITE);
  }

  @Override
  protected @Nullable PsiElement findAfterSuiteMethod(@NotNull PsiClass clazz) {
    return findMethod(clazz, PsiModifier.STATIC, ORG_JUNIT_PLATFORM_SUITE_API_AFTERSUITE);
  }

  @Override
  protected @Nullable PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    return findMethod(clazz, null, JUnitUtil.AFTER_EACH_ANNOTATION_NAME);
  }

  private static @Nullable PsiMethod findMethod(@NotNull PsiClass clazz,
                                                @PsiModifier.ModifierConstant @Nullable String modifier,
                                                @NotNull String annotationFqn) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if ((modifier == null || each.hasModifierProperty(modifier)) &&
            AnnotationUtil.isAnnotated(each, annotationFqn, 0)) {
          return each;
        }
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    PsiMethod method = findSetUpMethod(clazz);
    if (method != null) return method;

    PsiManager manager = clazz.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    method = createSetUpPatternMethod(factory);
    PsiMethod existingMethod = clazz.findMethodBySignature(method, false);
    if (existingMethod != null) {
      if (AnnotationUtil.isAnnotated(existingMethod, JUnitUtil.BEFORE_ALL_ANNOTATION_NAME, 0)) return existingMethod;
      int exit = ApplicationManager.getApplication().isUnitTestMode() ?
                 Messages.OK :
                 Messages.showOkCancelDialog(JUnitBundle.message("create.setup.dialog.message", "@BeforeEach"),
                                             JUnitBundle.message("create.setup.dialog.title"),
                                             Messages.getWarningIcon());
      if (exit == Messages.OK) {
        AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, PsiNameValuePair.EMPTY_ARRAY, 
                                                          existingMethod.getModifierList());
        return existingMethod;
      }
    }
    final PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
    if (testMethod != null) {
      method = (PsiMethod)clazz.addBefore(method, testMethod);
    } else {
      method = (PsiMethod)clazz.add(method);
    }
    JavaCodeStyleManager.getInstance(manager.getProject()).shortenClassReferences(method);

    return method;
  }

  @Override
  public boolean isIgnoredMethod(PsiElement element) {
    if (element == null) return false;
    return callWithAlternateResolver(element.getProject(), () -> {
      if (element instanceof PsiMethod method) {
        final PsiMethod ignoredTestMethod =
          AnnotationUtil.isAnnotated(method, "org.junit.jupiter.api.Disabled", 0) ? JUnitUtil.getTestMethod(element) : null;
        return ignoredTestMethod != null;
      }
      return false;
    }, true);
  }

  @Override
  public boolean acceptNestedClasses() {
    return true;
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 SetUp Method.java");
  }
  
  @Override
  public FileTemplateDescriptor getBeforeClassMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 BeforeAll Method.java");
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 TearDown Method.java");
  }

  @Override
  public FileTemplateDescriptor getAfterClassMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 AfterAll Method.java");
  }

  @Override
  public @NotNull FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 Test Method.java");
  }

  @Override
  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 Test Class.java");
  }

  @Override
  public boolean isSuiteClass(PsiClass psiClass) {
    if (psiClass == null) return false;
    return callWithAlternateResolver(psiClass.getProject(), () -> {
      return AnnotationUtil.isAnnotated(psiClass, ORG_JUNIT_PLATFORM_SUITE_API_SUITE, AnnotationUtil.CHECK_HIERARCHY);
    }, false);
  }
}
