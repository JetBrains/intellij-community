/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.execution.junit;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationFix;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;

import javax.swing.*;

public class JUnit4Framework extends JavaTestFramework {
  @NotNull
  public String getName() {
    return "JUnit4";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return JUnitConfigurationType.ICON;
  }

  protected String getMarkerClassFQName() {
    return "org.junit.Test";
  }

  @NotNull
  public String getLibraryPath() {
    return JavaSdkUtil.getJunit4JarPath();
  }

  @Nullable
  public String getDefaultSuperClass() {
    return null;
  }

  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (canBePotential) return isUnderTestSources(clazz);
    return JUnitUtil.isJUnit4TestClass(clazz);
  }

  @Nullable
  @Override
  protected PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    for (PsiMethod each : clazz.getMethods()) {
      if (AnnotationUtil.isAnnotated(each, "org.junit.Before", false)) return each;
    }
    return null;
  }

  @Nullable
  @Override
  protected PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    for (PsiMethod each : clazz.getMethods()) {
      if (AnnotationUtil.isAnnotated(each, "org.junit.After", false)) return each;
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

    method = factory.createMethodFromText("@org.junit.Before public void setUp() throws Exception {\n}", null);
    PsiMethod existingMethod = clazz.findMethodBySignature(method, false);
    if (existingMethod != null) {
      int exit = ApplicationManager.getApplication().isUnitTestMode() ?
                       DialogWrapper.OK_EXIT_CODE :
                       Messages.showOkCancelDialog("Method setUp already exist but is not annotated as @Before. Annotate?",
                                                   CommonBundle.getWarningTitle(),
                                                   Messages.getWarningIcon());
      if (exit == DialogWrapper.OK_EXIT_CODE) {
        new AddAnnotationFix("org.junit.Before", existingMethod).invoke(existingMethod.getProject(), null, existingMethod.getContainingFile());
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
    return testMethod != null && AnnotationUtil.isAnnotated(testMethod, Ignore.class.getName(), false);
  }

  @Override
  public boolean isTestMethod(PsiElement element) {
    return element instanceof PsiMethod && JUnitUtil.getTestMethod(element) != null;
  }

  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 SetUp Method.java");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 TearDown Method.java");
  }

  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit4 Test Method.java");
  }
}