// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;

/**
 * @author nik
 */
public class OverwriteProjectConfigurationTest extends PlatformTestCase {
  private File myProjectDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectDir = createTempDir(getTestDirectoryName());
  }

  public void testOverwriteModulesList() {
    final Project project = createProject();
    createModule(project, "module", ModuleTypeId.JAVA_MODULE);
    PlatformTestUtil.saveProject(project);
    ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));

    Project recreated = createProject();
    PlatformTestUtil.saveProject(recreated);
    assertEmpty(ModuleManager.getInstance(recreated).getModules());
  }

  public void testOverwriteModuleType() {
    final Project project = createProject();
    File imlFile = createModule(project, "module", ModuleTypeId.JAVA_MODULE);
    PlatformTestUtil.saveProject(project);
    assertTrue(imlFile.exists());
    ApplicationManager.getApplication().runWriteAction(() -> Disposer.dispose(project));

    Project recreated = createProject();
    createModule(recreated, "module", ModuleTypeId.WEB_MODULE);
    PlatformTestUtil.saveProject(recreated);
    Module module = assertOneElement(ModuleManager.getInstance(recreated).getModules());
    assertEquals(ModuleTypeId.WEB_MODULE, ModuleType.get(module).getId());
  }

  private Project createProject() {
    Project project = ProjectManagerEx.getInstanceEx().newProject("test", myProjectDir.getAbsolutePath());
    assertNotNull(project);
    disposeOnTearDown(project);
    return project;
  }

  private File createModule(final Project project, String moduleName, final String moduleTypeId) {
    final File imlFile = new File(myProjectDir, moduleName + ".iml");
    ApplicationManager.getApplication().runWriteAction(() -> {
      ModuleManager.getInstance(project).newModule(imlFile.getAbsolutePath(), moduleTypeId);
    });

    return imlFile;
  }
}
