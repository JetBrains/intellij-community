// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.junit;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JUnit3Framework extends JUnitTestFramework {

  @Override
  public boolean isDumbAware() {
    return this.getClass().isAssignableFrom(JUnit3Framework.class);
  }

  @Override
  public @NotNull String getName() {
    return "JUnit3";
  }

  @Override
  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 Test Class.java");
  }

  @Override
  public boolean isSingleConfig() {
    return true;
  }

  @Override
  public boolean isSuiteClass(PsiClass psiClass) {
    if (psiClass == null) return false;
    return callWithAlternateResolver(psiClass.getProject(), () -> {
      return JUnitUtil.findSuiteMethod(psiClass) != null;
    }, false);
  }

  @Override
  protected String getMarkerClassFQName() {
    return "junit.framework.TestCase";
  }

  @Override
  public @Nullable ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return JUnitExternalLibraryDescriptor.JUNIT3;
  }

  @Override
  public @Nullable String getDefaultSuperClass() {
    return "junit.framework.TestCase";
  }

  @Override
  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (clazz == null) return false;
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (JUnitUtil.isJUnit3TestClass(clazz)) {
        return true;
      }
      return JUnitUtil.findSuiteMethod(clazz) != null;
    }, false);
  }

  @Override
  protected @Nullable PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (!JUnitUtil.isJUnit3TestClass(clazz)) return null;

      for (PsiMethod each : clazz.getMethods()) {
        if (each.getName().equals("setUp")) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (!JUnitUtil.isJUnit3TestClass(clazz)) return null;
      for (PsiMethod each : clazz.getMethods()) {
        if (each.getName().equals("tearDown")) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    final PsiManager manager = clazz.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();

    final PsiMethod patternMethod = createSetUpPatternMethod(factory);

    final PsiClass baseClass = clazz.getSuperClass();
    if (baseClass != null) {
      final PsiMethod baseMethod = baseClass.findMethodBySignature(patternMethod, false);
      if (baseMethod != null && baseMethod.hasModifierProperty(PsiModifier.PUBLIC)) {
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PROTECTED, false);
        PsiUtil.setModifierProperty(patternMethod, PsiModifier.PUBLIC, true);
      }
    }

    PsiMethod inClass = clazz.findMethodBySignature(patternMethod, false);
    if (inClass == null) {
      PsiMethod testMethod = JUnitUtil.findFirstTestMethod(clazz);
      if (testMethod != null) {
        return (PsiMethod)clazz.addBefore(patternMethod, testMethod);
      }
      return (PsiMethod)clazz.add(patternMethod);
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 SetUp Method.java");
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 TearDown Method.java");
  }

  @Override
  public @NotNull FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("JUnit3 Test Method.java");
  }
}
