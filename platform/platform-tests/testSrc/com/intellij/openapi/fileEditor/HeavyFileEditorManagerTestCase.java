/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
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

  public void setUp() throws Exception {
    super.setUp();
    myManager = new FileEditorManagerImpl(getProject(), DockManager.getInstance(getProject()));
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
