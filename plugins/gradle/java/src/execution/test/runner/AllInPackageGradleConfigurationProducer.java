// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.testframework.AbstractJavaTestConfigurationProducer;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.TasksToRun;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.jetbrains.plugins.gradle.execution.test.runner.TestGradleConfigurationProducerUtilKt.getSourceFile;
import static org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestFilterFrom;

public class AllInPackageGradleConfigurationProducer extends AbstractGradleTestRunConfigurationProducer<PsiPackage, PsiPackage> {
  @Override
  public boolean isPreferredConfiguration(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return other.isProducedBy(AllInDirectoryGradleConfigurationProducer.class) || super.isPreferredConfiguration(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return other.isProducedBy(AllInDirectoryGradleConfigurationProducer.class) || super.shouldReplace(self, other);
  }

  @Override
  protected @Nullable PsiPackage getElement(@NotNull ConfigurationContext context) {
    Module module = context.getModule();
    if (module == null) return null;
    PsiElement psiLocation = context.getPsiLocation();
    if (psiLocation == null) return null;
    PsiElement sourceElement = getSourceElement(module, psiLocation);
    if (sourceElement == null) return null;
    VirtualFile source = getSourceFile(sourceElement);
    if (source == null) return null;
    return extractPackage(psiLocation);
  }

  @Override
  protected @NotNull String getLocationName(@NotNull ConfigurationContext context, @NotNull PsiPackage element) {
    return String.format("'%s'", element.getName());
  }

  @Override
  protected @NotNull String suggestConfigurationName(
    @NotNull ConfigurationContext context,
    @NotNull PsiPackage element,
    @NotNull List<? extends PsiPackage> chosenElements
  ) {
    return ExecutionBundle.message("test.in.scope.presentable.text", element.getQualifiedName());
  }

  @Override
  protected void chooseSourceElements(
    @NotNull ConfigurationContext context,
    @NotNull PsiPackage element,
    @NotNull Consumer<List<PsiPackage>> onElementsChosen
  ) {
    onElementsChosen.accept(Collections.emptyList());
  }

  @Override
  protected @NotNull List<TestTasksToRun> getAllTestsTaskToRun(
    @NotNull ConfigurationContext context,
    @NotNull PsiPackage element,
    @NotNull List<? extends PsiPackage> chosenElements
  ) {
    Project project = Objects.requireNonNull(context.getProject());
    Module module = Objects.requireNonNull(context.getModule());
    PsiElement psiLocation = Objects.requireNonNull(context.getPsiLocation());
    PsiElement sourceElement = Objects.requireNonNull(getSourceElement(module, psiLocation));
    VirtualFile source = Objects.requireNonNull(getSourceFile(sourceElement));
    List<TasksToRun> allTasksToRuns = findAllTestsTaskToRun(source, project);
    String testFilter = createTestFilterFrom(element);
    return ContainerUtil.map(allTasksToRuns, it -> new TestTasksToRun(it, testFilter));
  }

  @Nullable
  protected static PsiElement getSourceElement(@NotNull Module module, @NotNull PsiElement element) {
    if (element instanceof PsiFileSystemItem) {
      return element;
    }
    PsiFile containingFile = element.getContainingFile();
    if (containingFile != null) {
      return element;
    }
    if (element instanceof PsiPackage) {
      return getPackageDirectory(module, (PsiPackage)element);
    }
    return null;
  }

  @Nullable
  private static PsiDirectory getPackageDirectory(@NotNull Module module, @NotNull PsiPackage element) {
    PsiDirectory[] sourceDirs = element.getDirectories(GlobalSearchScope.moduleScope(module));
    if (sourceDirs.length == 0) return null;
    return sourceDirs[0];
  }

  @Nullable
  private static PsiPackage extractPackage(@NotNull PsiElement location) {
    PsiPackage psiPackage = AbstractJavaTestConfigurationProducer.checkPackage(location);
    if (psiPackage == null) return null;
    if (psiPackage.getQualifiedName().isEmpty()) return null;
    return psiPackage;
  }
}
