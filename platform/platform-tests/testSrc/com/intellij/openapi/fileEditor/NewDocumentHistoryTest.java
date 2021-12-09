// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import org.junit.Assert;

/**
 * @author Dmitry Avdeev
 */
public class NewDocumentHistoryTest extends HeavyFileEditorManagerTestCase {
  private IdeDocumentHistoryImpl myHistory;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myHistory = new IdeDocumentHistoryImpl(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      Disposer.dispose(myHistory);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      myHistory = null;
      super.tearDown();
    }
  }

  public void testBackNavigationBetweenEditors() {
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new FileEditorManagerTest.MyFileEditorProvider(), myFixture.getTestRootDisposable());
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(getProject());
    FileEditor[] editors = manager.openFile(file, true);
    assertEquals(2, editors.length);
    assertEquals("Text", manager.getSelectedEditor(file).getName());
    manager.setSelectedEditor(file, "mock");
    assertEquals(FileEditorManagerTest.MyFileEditorProvider.NAME, manager.getSelectedEditor(file).getName());
    manager.closeAllFiles();

    myHistory.back();
    assertEquals(FileEditorManagerTest.MyFileEditorProvider.NAME, manager.getSelectedEditor(file).getName());
  }

  public void testSelectFileOnNavigation() {
    VirtualFile file1 = getFile("/src/1.txt");
    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(getProject());
    manager.openFile(file1, true);
    VirtualFile file2 = getFile("/src/2.txt");
    manager.openFile(file2, true);
    NavigationUtil.activateFileWithPsiElement(PsiManager.getInstance(getProject()).findFile(file1));
    VirtualFile[] files = manager.getSelectedFiles();
    assertEquals(1, files.length);
    assertEquals("1.txt", files[0].getName());
  }

  public void testMergingCommands() {
    VirtualFile file1 = getFile("/src/1.txt");
    VirtualFile file2 = getFile("/src/2.txt");
    VirtualFile file3 = getFile("/src/3.txt");

    FileEditorManagerEx manager = FileEditorManagerEx.getInstanceEx(getProject());
    manager.openFile(file1, true);
    manager.openFile(file2, true);
    Object group = new Object();
    CommandProcessor.getInstance().executeCommand(getProject(), () -> {}, null, group);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> manager.openFile(file3, true), null, group);
    myHistory.back();
    VirtualFile[] selectedFiles = manager.getSelectedFiles();
    Assert.assertArrayEquals(new VirtualFile[] {file2}, selectedFiles);
  }
}
