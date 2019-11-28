// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

/**
 * @author nik
 */
public class OverwriteProjectConfigurationTest extends HeavyPlatformTestCase {
  private Path myProjectDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectDir = createTempDir(getTestDirectoryName()).toPath();
  }

  public void testOverwriteModulesList() {
    Project project = ProjectManagerEx.getInstanceEx().newProjectForTest(myProjectDir);
    try {
      createModule(project, "module", ModuleTypeId.JAVA_MODULE);
      PlatformTestUtil.saveProject(project);
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));
    }

    Project recreated = createProject();
    PlatformTestUtil.saveProject(recreated);
    assertThat(ModuleManager.getInstance(recreated).getModules()).isEmpty();
  }

  public void testOverwriteModuleType() {
    Project project = ProjectManagerEx.getInstanceEx().newProjectForTest(myProjectDir);
    try {
      Path imlFile = createModule(project, "module", ModuleTypeId.JAVA_MODULE);
      PlatformTestUtil.saveProject(project);
      assertThat(imlFile).isRegularFile();
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));
    }

    Project recreated = createProject();
    createModule(recreated, "module", ModuleTypeId.WEB_MODULE);
    PlatformTestUtil.saveProject(recreated);
    Module module = assertOneElement(ModuleManager.getInstance(recreated).getModules());
    assertEquals(ModuleTypeId.WEB_MODULE, ModuleType.get(module).getId());
  }

  @NotNull
  private Project createProject() {
    Project project = ProjectManagerEx.getInstanceEx().newProjectForTest(myProjectDir);
    Disposer.register(myProject, new Disposable() {
      @Override
      public void dispose() {
        ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));
      }
    });
    return project;
  }

  @NotNull
  private Path createModule(@NotNull Project project, @NotNull String moduleName, @NotNull String moduleTypeId) {
    Path imlFile = myProjectDir.resolve(moduleName + ".iml");
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleManager.getInstance(project).newModule(imlFile.toAbsolutePath().toString(), moduleTypeId);
    });
    return imlFile;
  }
}
