// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.junit;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.icons.AllIcons;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class JUnit5Framework extends JavaTestFramework {
  @NotNull
  public String getName() {
    return "JUnit5";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return AllIcons.RunConfigurations.Junit;
  }

  protected String getMarkerClassFQName() {
    return JUnitUtil.TEST5_ANNOTATION;
  }

  @Nullable
  @Override
  public ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT5;
  }

  @Nullable
  public String getDefaultSuperClass() {
    return null;
  }

  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (canBePotential) return isUnderTestSources(clazz);
    return JUnitUtil.isJUnit5TestClass(clazz, false);
  }

  @Nullable
  @Override
  protected PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    for (PsiMethod each : clazz.getMethods()) {
      if (AnnotationUtil.isAnnotated(each, JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, 0)) return each;
    }
    return null;
  }

  @Nullable
  @Override
  protected PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    for (PsiMethod each : clazz.getMethods()) {
      if (AnnotationUtil.isAnnotated(each, JUnitUtil.AFTER_EACH_ANNOTATION_NAME, 0)) return each;
    }
    return null;
  }

  @Override
  @Nullable
  protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
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
                       Messages.showOkCancelDialog("Method setUp already exist but is not annotated as @BeforeEach. Annotate?",
                                                   CommonBundle.getWarningTitle(),
                                                   Messages.getWarningIcon());
      if (exit == Messages.OK) {
        new AddAnnotationFix(JUnitUtil.BEFORE_EACH_ANNOTATION_NAME, existingMethod).invoke(existingMethod.getProject(), null, existingMethod.getContainingFile());
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
    final PsiMethod testMethod = element instanceof PsiMethod ? JUnitUtil.getTestMethod(element) : null;
    return testMethod != null && AnnotationUtil.isAnnotated(testMethod, JUnitUtil.IGNORE_ANNOTATION, 0);
  }

  @Override
  public boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    return element instanceof PsiMethod && JUnitUtil.getTestMethod(element, checkAbstract) != null;
  }

  @Override
  public boolean isTestMethod(PsiMethod method, PsiClass myClass) {
    return JUnitUtil.isTestMethod(MethodLocation.elementInClass(method, myClass));
  }

  @Override
  public boolean isMyConfigurationType(ConfigurationType type) {
    return type instanceof JUnitConfigurationType;
  }

  @Override
  public boolean acceptNestedClasses() {
    return true;
  }

  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 SetUp Method.java");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 TearDown Method.java");
  }

  @NotNull
  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 Test Method.java");
  }

  @Override
  public char getMnemonic() {
    return '5';
  }

  @Override
  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit5 Test Class.java");
  }
}
