// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.execution.JUnitBundle;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnit4Framework extends JUnitTestFramework {

  @Override
  public boolean isDumbAware() {
    return this.getClass().isAssignableFrom(JUnit4Framework.class);
  }

  @Override
  public @NotNull String getName() {
    return "JUnit4";
  }

  @Override
  protected String getMarkerClassFQName() {
    return JUnitUtil.TEST_ANNOTATION;
  }

  @Override
  public @Nullable ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT4;
  }

  @Override
  public @Nullable String getDefaultSuperClass() {
    return null;
  }

  @Override
  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (clazz == null) return false;
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (canBePotential) return isUnderTestSources(clazz);
      if (!isFrameworkAvailable(clazz)) return false;
      return JUnitUtil.isJUnit4TestClass(clazz, false);
    }, false);
  }

  @Override
  protected @Nullable PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (AnnotationUtil.isAnnotated(each, JUnitUtil.BEFORE_ANNOTATION_NAME, 0)) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findBeforeClassMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (each.hasModifierProperty(PsiModifier.STATIC)
            && AnnotationUtil.isAnnotated(each, JUnitUtil.BEFORE_ANNOTATION_NAME, 0)) {
          return each;
        }
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (AnnotationUtil.isAnnotated(each, JUnitUtil.AFTER_ANNOTATION_NAME, 0)) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findAfterClassMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (each.hasModifierProperty(PsiModifier.STATIC)
            && AnnotationUtil.isAnnotated(each, JUnitUtil.AFTER_CLASS_ANNOTATION_NAME, 0)) {
          return each;
        }
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    String beforeClassAnnotationName = JUnitUtil.BEFORE_CLASS_ANNOTATION_NAME;
    String beforeAnnotationName = JUnitUtil.BEFORE_ANNOTATION_NAME;
    return findOrCreateSetUpMethod(clazz, beforeClassAnnotationName, beforeAnnotationName);
  }

  private PsiMethod findOrCreateSetUpMethod(PsiClass clazz, String beforeClassAnnotationName, String beforeAnnotationName) {
    PsiMethod method = findSetUpMethod(clazz);
    if (method != null) return method;

    PsiManager manager = clazz.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    method = createSetUpPatternMethod(factory);
    PsiMethod existingMethod = clazz.findMethodBySignature(method, false);
    if (existingMethod != null) {
      if (AnnotationUtil.isAnnotated(existingMethod, beforeClassAnnotationName, 0)) return existingMethod;
      int exit = ApplicationManager.getApplication().isUnitTestMode() ?
                 Messages.OK :
                 Messages.showOkCancelDialog(JUnitBundle.message("create.setup.dialog.message", "@Before"),
                                             JUnitBundle.message("create.setup.dialog.title"),
                                             Messages.getWarningIcon());
      if (exit == Messages.OK) {
        AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(beforeAnnotationName, PsiNameValuePair.EMPTY_ARRAY, existingMethod.getModifierList());
        return existingMethod;
      }
    }
    final PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
    if (testMethod != null) {
      method = (PsiMethod)clazz.addBefore(method, testMethod);
    }
    else {
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
          AnnotationUtil.isAnnotated(method, "org.junit.Ignore", 0) ? JUnitUtil.getTestMethod(element) : null;
        return ignoredTestMethod != null;
      }
      return false;
    }, true);
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 SetUp Method.java");
  }

  @Override
  public FileTemplateDescriptor getBeforeClassMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 BeforeClass Method.java");
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 TearDown Method.java");
  }

  @Override
  public FileTemplateDescriptor getAfterClassMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 AfterClass Method.java");
  }

  @Override
  public @NotNull FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 Test Method.java");
  }

  @Override
  public FileTemplateDescriptor getParametersMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 Parameters Method.java");
  }

  @Override
  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 Test Class.java");
  }

  @Override
  public boolean isSuiteClass(PsiClass psiClass) {
    if (psiClass == null) return false;
    return callWithAlternateResolver(psiClass.getProject(), ()->{
      PsiAnnotation annotation = JUnitUtil.getRunWithAnnotation(psiClass);
      return annotation != null && JUnitUtil.isOneOf(annotation, "org.junit.runners.Suite");
    }, false);
  }

  @Override
  public boolean isParameterized(PsiClass clazz) {
    if (clazz == null) return false;
    return callWithAlternateResolver(clazz.getProject(), () -> {
      PsiAnnotation annotation = JUnitUtil.getRunWithAnnotation(clazz);
      return annotation != null && JUnitUtil.isParameterized(annotation);
    }, false);
  }

  @Override
  public PsiMethod findParametersMethod(PsiClass clazz) {
    if(clazz == null) return null;
    return callWithAlternateResolver(clazz.getProject(), ()->{
      final PsiMethod[] methods = clazz.getAllMethods();
      for (PsiMethod method : methods) {
        if (method.hasModifierProperty(PsiModifier.PUBLIC) &&
            method.hasModifierProperty(PsiModifier.STATIC) &&
            AnnotationUtil.isAnnotated(method, "org.junit.runners.Parameterized.Parameters", 0)) {
          //todo check return value
          return method;
        }
      }
      return null;
    }, null);
  }
}
