// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.ui.docking.DockManager;

/**
 * @author Dmitry Avdeev
 */
public abstract class HeavyFileEditorManagerTestCase extends CodeInsightFixtureTestCase {
  protected FileEditorManagerImpl myManager;

  protected VirtualFile getFile(String path) {
    return LocalFileSystem.getInstance().refreshAndFindFileByPath(
      PlatformTestUtil.getPlatformTestDataPath() + "fileEditorManager" + path);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myManager = new FileEditorManagerImpl(getProject(), DockManager.getInstance(getProject()));
    ((IdeDocumentHistoryImpl)IdeDocumentHistory.getInstance(getProject())).setFileEditorManager(myManager);
    ((ComponentManagerImpl)getProject()).registerComponentInstance(FileEditorManager.class, myManager);
  }

  @Override
  protected void tearDown() throws Exception {
    myManager = null;

    super.tearDown();
  }

  @Override
  protected String getBasePath() {
    return "/platform/platform-tests/testData/fileEditorManager";
  }
}
