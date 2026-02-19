// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jetbrains.plugins.gradle.util.GradleExecutionSettingsUtil.createTestWildcardFilter;

public class AllInDirectoryGradleConfigurationProducer extends AbstractGradleTestRunConfigurationProducer<PsiElement, PsiElement> {
  @Override
  public boolean isPreferredConfiguration(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(AllInPackageGradleConfigurationProducer.class) && super.isPreferredConfiguration(self, other);
  }

  @Override
  public boolean shouldReplace(@NotNull ConfigurationFromContext self, @NotNull ConfigurationFromContext other) {
    return !other.isProducedBy(AllInPackageGradleConfigurationProducer.class) && super.shouldReplace(self, other);
  }

  @Override
  protected @Nullable PsiElement getElement(@NotNull ConfigurationContext context) {
    PsiElement psiElement = context.getPsiLocation();
    if (psiElement instanceof PsiFileSystemItem &&
        ((PsiFileSystemItem)psiElement).isDirectory()) {
      return psiElement;
    }
    return null;
  }

  @Override
  protected @NotNull String getLocationName(@NotNull ConfigurationContext context, @NotNull PsiElement element) {
    Module module = Objects.requireNonNull(context.getModule());
    return String.format("'%s'", module.getName());
  }

  @Override
  protected @NotNull String suggestConfigurationName(
    @NotNull ConfigurationContext context,
    @NotNull PsiElement element,
    @NotNull List<? extends PsiElement> chosenElements
  ) {
    Module module = Objects.requireNonNull(context.getModule());
    return ExecutionBundle.message("test.in.scope.presentable.text", module.getName());
  }

  @Override
  protected void chooseSourceElements(
    @NotNull ConfigurationContext context,
    @NotNull PsiElement element,
    @NotNull Consumer<List<PsiElement>> onElementsChosen
  ) {
    onElementsChosen.accept(Collections.emptyList());
  }

  @Override
  protected @NotNull List<TestTasksToRun> getAllTestsTaskToRun(
    @NotNull ConfigurationContext context,
    @NotNull PsiElement element,
    @NotNull List<? extends PsiElement> chosenElements
  ) {
    Project project = Objects.requireNonNull(context.getProject());
    Module module = Objects.requireNonNull(context.getModule());
    VirtualFile directory = ((PsiFileSystemItem)element).getVirtualFile();
    List<VirtualFile> sources = findTestSourcesUnderDirectory(module, directory);
    String testFilter = createTestWildcardFilter();
    List<TestTasksToRun> testsTasksToRun = new ArrayList<>();
    for (VirtualFile source : sources) {
      testsTasksToRun.addAll(ContainerUtil.map(findAllTestsTaskToRun(source, project), it -> new TestTasksToRun(it, testFilter)));
    }
    return testsTasksToRun;
  }

  private static List<VirtualFile> findTestSourcesUnderDirectory(@NotNull Module module, @NotNull VirtualFile directory) {
    DataNode<ModuleData> moduleDataNode = GradleUtil.findGradleModuleData(module);
    if (moduleDataNode == null) return Collections.emptyList();
    String rootPath = directory.getPath();
    return ExternalSystemApiUtil.findAll(moduleDataNode, ProjectKeys.TEST).stream()
      .map(DataNode::getData)
      .flatMap(it -> it.getSourceFolders().stream())
      .filter(it -> FileUtil.isAncestor(rootPath, it, false))
      .map(it -> VfsUtil.findFile(Paths.get(it), false))
      .filter(Objects::nonNull)
      .distinct()
      .collect(Collectors.toList());
  }
}
