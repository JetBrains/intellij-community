// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit2.PsiMemberParameterizedLocation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.jetbrains.plugins.gradle.execution.GradleRunnerUtil.getMethodLocation;
import static org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestFilterFrom;

public class TestMethodGradleConfigurationProducer extends AbstractGradleTestRunConfigurationProducer<PsiMethod, PsiClass> {

  @Override
  public boolean isPreferredConfiguration(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(PatternGradleConfigurationProducer.class) &&
           (other.isProducedBy(TestClassGradleConfigurationProducer.class) ||
            super.isPreferredConfiguration(self, other));
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(PatternGradleConfigurationProducer.class) &&
           (other.isProducedBy(TestClassGradleConfigurationProducer.class) ||
            super.shouldReplace(self, other));
  }

  protected @Nullable PsiMethod getPsiMethodForLocation(@NotNull Location<?> contextLocation) {
    Location<PsiMethod> location = getMethodLocation(contextLocation);
    return location != null ? location.getPsiElement() : null;
  }

  @Override
  protected @Nullable PsiMethod getElement(@NotNull ConfigurationContext context) {
    Location<?> location = context.getLocation();
    if (location == null) return null;
    PsiMethod psiMethod = getPsiMethodForLocation(location);
    if (psiMethod == null) return null;
    PsiClass psiClass = getContainingClass(location, psiMethod);
    if (psiClass == null || psiClass.getName() == null || psiClass.getQualifiedName() == null) return null;
    PsiFile psiFile = psiMethod.getContainingFile();
    if (psiFile == null) return null;
    VirtualFile source = psiFile.getVirtualFile();
    if (source == null) return null;
    return psiMethod;
  }

  @Override
  protected @NotNull String getLocationName(@NotNull ConfigurationContext context, @NotNull PsiMethod element) {
    return element.getName();
  }

  @Override
  protected @NotNull String suggestConfigurationName(
    @NotNull ConfigurationContext context,
    @NotNull PsiMethod element,
    @NotNull List<? extends PsiClass> chosenElements
  ) {
    PsiClass psiClass = Objects.requireNonNull(getContainingClass(context.getLocation(), element));
    List<? extends PsiClass> elements = chosenElements.isEmpty() ? List.of(psiClass) : chosenElements;
    return StringUtil.join(elements, aClass -> aClass.getName() + "." + element.getName(), "|");
  }

  @Override
  protected void chooseSourceElements(
    @NotNull ConfigurationContext context,
    @NotNull PsiMethod element,
    @NotNull Consumer<List<PsiClass>> onElementsChosen
  ) {
    PsiClass psiClass = Objects.requireNonNull(getContainingClass(context.getLocation(), element));
    InheritorChooser.chooseAbstractClassInheritors(context, psiClass, onElementsChosen);
  }

  @Override
  protected @NotNull List<TestTasksToRun> getAllTestsTaskToRun(
    @NotNull ConfigurationContext context,
    @NotNull PsiMethod element,
    @NotNull List<? extends PsiClass> chosenElements
  ) {
    Project project = Objects.requireNonNull(context.getProject());
    Location<?> location = Objects.requireNonNull(context.getLocation());
    VirtualFile source = Objects.requireNonNull(element.getContainingFile().getVirtualFile());
    PsiClass aClass = Objects.requireNonNull(getContainingClass(location, element));
    List<? extends PsiClass> elements = chosenElements.isEmpty() ? List.of(aClass) : chosenElements;
    List<TestTasksToRun> testsTasksToRun = new ArrayList<>();
    for (PsiClass psiClass : elements) {
      String testFilter = createTestFilterFrom(location, psiClass, element);
      testsTasksToRun.addAll(ContainerUtil.map(findAllTestsTaskToRun(source, project), it -> new TestTasksToRun(it, testFilter)));
    }
    return testsTasksToRun;
  }

  private static @Nullable PsiClass getContainingClass(@Nullable Location<?> location, @NotNull PsiMethod element) {
    if (location instanceof PsiMemberParameterizedLocation memberParameterizedLocation) {
      return memberParameterizedLocation.getContainingClass();
    }
    else {
      return element.getContainingClass();
    }
  }
}
