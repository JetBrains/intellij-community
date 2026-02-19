// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.spock;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.execution.junit.JUnitUtil;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.testIntegration.GroovyTestFramework;

import static com.intellij.psi.util.InheritanceUtil.isInheritor;

public final class SpockTestFramework extends GroovyTestFramework implements DumbAware {
  private static final ExternalLibraryDescriptor SPOCK_DESCRIPTOR = new ExternalLibraryDescriptor("org.spockframework", "spock-core");

  @Override
  public @NotNull String getName() {
    return "Spock";
  }

  @Override
  public String getLibraryPath() {
    return null;
  }

  @Override
  public ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return SPOCK_DESCRIPTOR;
  }

  @Override
  public @NotNull String getDefaultSuperClass() {
    return SpockUtils.SPEC_CLASS_NAME;
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Spock_SetUp_Method.groovy");
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Spock cleanup Method.groovy");
  }

  @Override
  public @NotNull FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("Spock Test Method.groovy");
  }

  @Override
  public boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    if (element == null) return false;
    return callWithAlternateResolver(element.getProject(), () -> {
      return SpockUtils.isTestMethod(element);
    }, false);
  }

  @Override
  protected String getMarkerClassFQName() {
    return SpockUtils.SPEC_CLASS_NAME;
  }

  @Override
  protected boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (clazz == null) return false;
    return callWithAlternateResolver(clazz.getProject(), () -> {
      return clazz.getLanguage() == GroovyLanguage.INSTANCE && isInheritor(clazz, SpockUtils.SPEC_CLASS_NAME);
    }, false);
  }

  private @Nullable PsiMethod findSpecificMethod(@NotNull PsiClass clazz, String methodName) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (!isTestClass(clazz, false)) return null;

      for (PsiMethod method : clazz.findMethodsByName(methodName, false)) {
        if (method.getParameterList().isEmpty()) return method;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    return findSpecificMethod(clazz, SpockConstants.SETUP_METHOD_NAME);
  }

  @Override
  protected @Nullable PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    return findSpecificMethod(clazz, SpockConstants.CLEANUP_METHOD_NAME);
  }

  @Override
  public boolean shouldRunSingleClassAsJUnit5(Project project, GlobalSearchScope scope) {
    return callWithAlternateResolver(project, () -> {
      PsiClass aClass = JavaPsiFacade.getInstance(project).findClass(SpockUtils.SPEC_CLASS_NAME, scope);
      return aClass != null && AnnotationUtil.isAnnotated(aClass, JUnitUtil.CUSTOM_TESTABLE_ANNOTATION, 0);
    }, false);
  }
}
