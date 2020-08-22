// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModulesConfigurationTest extends HeavyPlatformTestCase {
  public void testAddRemoveModule() throws IOException {
    Pair<Path, Path> result = createProjectWithModule();
    Path projectDir = result.getFirst();

    Project reloaded = PlatformTestUtil.loadAndOpenProject(projectDir);
    closeOnTearDown(reloaded);
    ModuleManager moduleManager = ModuleManager.getInstance(reloaded);
    Module module = assertOneElement(moduleManager.getModules());
    moduleManager.disposeModule(module);
    closeProject(reloaded, true);

    reloaded = PlatformTestUtil.loadAndOpenProject(projectDir);
    closeOnTearDown(reloaded);
    assertEmpty(ModuleManager.getInstance(reloaded).getModules());
    closeProject(reloaded, false);
  }

  // because of external storage, imls file can be missed on disk and it is not error
  public void testRemoveFailedToLoadModule() throws IOException {
    Pair<Path, Path> result = createProjectWithModule();
    Path projectDir = result.getFirst();
    Path moduleFile = result.getSecond();

    assertThat(moduleFile).exists();
    WriteAction.run(() -> LocalFileSystem.getInstance().refreshAndFindFileByPath(FileUtil.toSystemIndependentName(moduleFile.toString())).delete(this));
    List<ConfigurationErrorDescription> errors = new ArrayList<>();
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(errors::add, getTestRootDisposable());
    Project reloaded = PlatformTestUtil.loadAndOpenProject(projectDir);
    closeOnTearDown(reloaded);
    ModuleManager moduleManager = ModuleManager.getInstance(reloaded);
    assertThat(moduleManager.getModules()).hasSize(1);
    assertThat(errors).isEmpty();
    closeProject(reloaded, true);
    errors.clear();

    reloaded = PlatformTestUtil.loadAndOpenProject(projectDir);
    closeOnTearDown(reloaded);
    assertEmpty(errors);
    closeProject(reloaded, false);
  }

  private @NotNull Pair<Path, Path> createProjectWithModule() throws IOException {
    Path projectDir = FileUtil.createTempDirectory("project", null).toPath();
    Project project = PlatformTestUtil.loadAndOpenProject(projectDir);
    closeOnTearDown(project);
    Path moduleFile = projectDir.resolve("module.iml");
    WriteAction.run(() -> ModuleManager.getInstance(project).newModule(moduleFile.toString(), EmptyModuleType.EMPTY_MODULE));
    closeProject(project, true);
    return new Pair<>(projectDir, moduleFile);
  }

  private static void closeProject(@NotNull Project project, boolean isSave) {
    if (isSave) {
      StateStorageManagerKt.saveComponentManager(project, true);
    }
    PlatformTestUtil.forceCloseProjectWithoutSaving(project);
  }

  private void closeOnTearDown(Project project) {
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        if (!project.isDisposed()) {
          PlatformTestUtil.forceCloseProjectWithoutSaving(project);
        }
      }
    });
  }
}
