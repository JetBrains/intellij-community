// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module;

import com.intellij.configurationStore.StateStorageManagerKt;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.ProjectManagerImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.testFramework.PlatformTestCase;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ModulesConfigurationTest extends PlatformTestCase {
  public void testAddRemoveModule() throws IOException, JDOMException {
    Pair<File, File> result = createProjectWithModule();
    File projectDir = result.getFirst();

    ProjectManager projectManager = ProjectManager.getInstance();
    Project reloaded = projectManager.loadAndOpenProject(projectDir);
    disposeOnTearDown(reloaded);
    ModuleManager moduleManager = ModuleManager.getInstance(reloaded);
    Module module = assertOneElement(moduleManager.getModules());
    moduleManager.disposeModule(module);
    closeProject(reloaded, true);

    reloaded = projectManager.loadAndOpenProject(projectDir);
    disposeOnTearDown(reloaded);
    assertEmpty(ModuleManager.getInstance(reloaded).getModules());
    closeProject(reloaded, false);
  }

  // because of external storage, imls file can be missed on disk and it is not error
  public void testRemoveFailedToLoadModule() throws IOException, JDOMException {
    Pair<File, File> result = createProjectWithModule();
    File projectDir = result.getFirst();
    File moduleFile = result.getSecond();

    assertThat(moduleFile).exists();
    WriteAction.run(() -> LocalFileSystem.getInstance().refreshAndFindFileByIoFile(moduleFile).delete(this));
    List<ConfigurationErrorDescription> errors = new ArrayList<>();
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(errors::add, getTestRootDisposable());
    ProjectManager projectManager = ProjectManager.getInstance();
    Project reloaded = projectManager.loadAndOpenProject(projectDir);
    disposeOnTearDown(reloaded);
    ModuleManager moduleManager = ModuleManager.getInstance(reloaded);
    assertThat(moduleManager.getModules()).hasSize(1);
    assertThat(errors).isEmpty();
    closeProject(reloaded, true);
    errors.clear();

    reloaded = projectManager.loadAndOpenProject(projectDir);
    disposeOnTearDown(reloaded);
    assertEmpty(errors);
    closeProject(reloaded, false);
  }

  @NotNull
  private Pair<File, File> createProjectWithModule() throws IOException {
    File projectDir = FileUtil.createTempDirectory("project", null);
    Project project = ProjectManager.getInstance().createProject("project", projectDir.getAbsolutePath());
    disposeOnTearDown(project);
    File moduleFile = new File(projectDir, "module.iml");
    WriteAction.run(() -> ModuleManager.getInstance(project).newModule(moduleFile.getPath(), EmptyModuleType.EMPTY_MODULE));
    closeProject(project, true);
    return Pair.create(projectDir, moduleFile);
  }

  private static void closeProject(@NotNull Project project, boolean isSave) {
    if (isSave) {
      StateStorageManagerKt.saveComponentManager(project, true);
    }
    ((ProjectManagerImpl)ProjectManager.getInstance()).forceCloseProject(project, true);
  }
}
