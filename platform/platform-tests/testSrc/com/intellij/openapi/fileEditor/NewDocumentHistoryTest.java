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

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestUtil;

/**
 * @author Dmitry Avdeev
 *         Date: 4/29/13
 */
public class NewDocumentHistoryTest extends HeavyFileEditorManagerTestCase {
  private IdeDocumentHistoryImpl myHistory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHistory = new IdeDocumentHistoryImpl(getProject(), EditorFactory.getInstance(),
                                           myManager, VirtualFileManager.getInstance(), CommandProcessor.getInstance(), ToolWindowManager
                                             .getInstance(getProject()));
    myHistory.projectOpened();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myHistory.projectClosed();
      myHistory.disposeComponent();
    }
    finally {
      myHistory = null;
      super.tearDown();
    }
  }

  public void testBackNavigationBetweenEditors() {
    PlatformTestUtil.registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new FileEditorManagerTest.MyFileEditorProvider(), myFixture.getTestRootDisposable());
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    FileEditor[] editors = myManager.openFile(file, true);
    assertEquals(2, editors.length);
    assertEquals("Text", myManager.getSelectedEditor(file).getName());
    myManager.setSelectedEditor(file, "mock");
    assertEquals("mockEditor", myManager.getSelectedEditor(file).getName());
    myManager.closeAllFiles();

    myHistory.back();
    assertEquals("mockEditor", myManager.getSelectedEditor(file).getName());
  }

  public void testSelectFileOnNavigation() {
    VirtualFile file1 = getFile("/src/1.txt");
    myManager.openFile(file1, true);
    VirtualFile file2 = getFile("/src/2.txt");
    myManager.openFile(file2, true);
    NavigationUtil.activateFileWithPsiElement(PsiManager.getInstance(getProject()).findFile(file1));
    VirtualFile[] files = myManager.getSelectedFiles();
    assertEquals(1, files.length);
    assertEquals("1.txt", files[0].getName());
  }
}
