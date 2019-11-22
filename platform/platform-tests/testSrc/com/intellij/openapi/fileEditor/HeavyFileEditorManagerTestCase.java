// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.builders.ModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public abstract class HeavyFileEditorManagerTestCase extends CodeInsightFixtureTestCase<ModuleFixtureBuilder<?>> {
  protected VirtualFile getFile(String path) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(
      PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager" + path);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    Project project = getProject();
    FileEditorManagerImpl manager = new FileEditorManagerImpl(project);
    ServiceContainerUtil.registerComponentInstance(project, FileEditorManager.class, manager, getTestRootDisposable());
  }

  @Override
  protected String getBasePath() {
    return "/platform/platform-tests/testData/fileEditorManager";
  }
}
