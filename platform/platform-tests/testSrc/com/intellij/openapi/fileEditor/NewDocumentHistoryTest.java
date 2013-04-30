package com.intellij.openapi.fileEditor;

import com.intellij.codeInsight.navigation.NavigationUtil;
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;

/**
 * @author Dmitry Avdeev
 *         Date: 4/29/13
 */
public class NewDocumentHistoryTest extends HeavyFileEditorManagerTestCase {

  public NewDocumentHistoryTest() {
    PlatformTestCase.initPlatformLangPrefix();
  }

  public void testBackNavigationBetweenEditors() throws Exception {
    PlatformTestUtil.registerExtension(FileEditorProvider.EP_FILE_EDITOR_PROVIDER, new FileEditorManagerTest.MyFileEditorProvider(), getTestRootDisposable());
    VirtualFile file = getFile("/src/1.txt");
    assertNotNull(file);
    FileEditor[] editors = myManager.openFile(file, true);
    assertEquals(2, editors.length);
    assertEquals("Text", myManager.getSelectedEditor(file).getName());
    myManager.setSelectedEditor(file, "mock");
    assertEquals("mockEditor", myManager.getSelectedEditor(file).getName());
    myManager.closeAllFiles();

    IdeDocumentHistory.getInstance(getProject()).back();
    assertEquals("mockEditor", myManager.getSelectedEditor(file).getName());
  }

  public void testSelectFileOnNavigation() throws Exception {
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
