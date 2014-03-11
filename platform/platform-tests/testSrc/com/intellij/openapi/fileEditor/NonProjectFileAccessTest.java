/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.components.impl.ComponentManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileEditor.impl.NonProjectFileNotificationPanel;
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WritingAccessProvider;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

public class NonProjectFileAccessTest extends HeavyFileEditorManagerTestCase {
  @Override
  public void setUp() throws Exception {
    PlatformTestCase.initPlatformLangPrefix();
    super.setUp();
    EditorNotifications notifications = new EditorNotifications(getProject(), myManager);
    ((ComponentManagerImpl)getProject()).registerComponentInstance(EditorNotifications.class, notifications);
    NonProjectFileWritingAccessProvider.enableChecksInTests(getProject(), true);
  }

  @Override
  protected void tearDown() throws Exception {
    NonProjectFileWritingAccessProvider.enableChecksInTests(getProject(), false);
    super.tearDown();
  }

  @Override
  protected boolean runInDispatchThread() {
    return true;
  }

  public void testBasicAccessCheck() throws Exception {
    VirtualFile projectFile = createProjectFile();
    typeAndCheck(projectFile, true);
    typeAndCheck(projectFile, true); // still allowed

    VirtualFile nonProjectFile = createNonProjectFile();
    typeAndCheck(nonProjectFile, false);
    NonProjectFileNotificationPanel panel = typeAndCheck(nonProjectFile, false);// still not allowed

    panel.getUnlockAction().doClick();
    assertNull(getNotificationPanel(projectFile));
    assertNull(getNotificationPanel(nonProjectFile));

    typeAndCheck(nonProjectFile, true);
    assertNull(getNotificationPanel(projectFile));
    assertNull(getNotificationPanel(nonProjectFile));
  }

  public void testAllowEditingInOneFileOnly() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    NonProjectFileNotificationPanel panel1 = typeAndCheck(nonProjectFile1, false);
    NonProjectFileNotificationPanel panel2 = typeAndCheck(nonProjectFile2, false);

    panel1.getUnlockAction().doClick();
    assertNull(getNotificationPanel(nonProjectFile1));
    assertNotNull(getNotificationPanel(nonProjectFile2));

    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, false);

    panel2.getUnlockAction().doClick();
    assertNull(getNotificationPanel(nonProjectFile1));
    assertNull(getNotificationPanel(nonProjectFile2));

    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, true);
  }

  public void testAllowEditingInAllFiles() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    NonProjectFileNotificationPanel panel1 = typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    assertNotNull(getNotificationPanel(nonProjectFile1));
    assertNotNull(getNotificationPanel(nonProjectFile2));

    panel1.getUnlockAllLabel().doClick();
    assertNull(getNotificationPanel(nonProjectFile1));
    assertNull(getNotificationPanel(nonProjectFile2));

    typeAndCheck(nonProjectFile1, true);
    typeAndCheck(nonProjectFile2, true);
  }

  public void testCheckingOtherWriteAccessProvidersOnUnlock() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    final List<VirtualFile> requested = registerWriteAccessProvider(nonProjectFile1);

    NonProjectFileNotificationPanel panel1 = typeAndCheck(nonProjectFile1, false);
    panel1.getUnlockAction().doClick();
    assertSameElements(requested, nonProjectFile1);
    typeAndCheck(nonProjectFile1, false); // leave file locked if other provides denied access 

    requested.clear();

    NonProjectFileNotificationPanel panel2 = typeAndCheck(nonProjectFile2, false);
    panel2.getUnlockAction().doClick();
    assertSameElements(requested, nonProjectFile2);
    typeAndCheck(nonProjectFile2, true);
  }

  public void testCheckingOtherWriteAccessProvidersOnUnlockAll() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    final List<VirtualFile> requested = registerWriteAccessProvider(nonProjectFile1);

    NonProjectFileNotificationPanel panel = typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    assertNotNull(getNotificationPanel(nonProjectFile1));
    assertNotNull(getNotificationPanel(nonProjectFile2));

    panel.getUnlockAllLabel().doClick();
    assertSameElements(requested, nonProjectFile1);

    typeAndCheck(nonProjectFile1, false, false); // can't write, but access panel is not shown 
    typeAndCheck(nonProjectFile2, true);
  }

  public void testClearingInfoForDeletedFiles() throws Exception {
    final VirtualFile nonProjectFile1 = createNonProjectFile();
    final VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    new WriteAction<Object>() {
      @Override
      protected void run(@NotNull Result<Object> result) throws Throwable {
        nonProjectFile1.delete(this);
      }
    }.execute();

    assertNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));
  }

  public void testUpdatingNotificationsOnRootChanges() throws Exception {
    VirtualFile nonProjectFile1 = createNonProjectFile();
    VirtualFile nonProjectFile2 = createNonProjectFile();

    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);

    assertNotNull(getNotificationPanel(nonProjectFile1));
    assertNotNull(getNotificationPanel(nonProjectFile2));

    ContentEntry contextRoot = PsiTestUtil.addContentRoot(myModule, nonProjectFile2.getParent());
    
    // removing notification panel for newly added files
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    PsiTestUtil.removeContentEntry(myModule, contextRoot);

    // do not add notification panel until access is requested
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));

    // but files are still not writable
    typeAndCheck(nonProjectFile1, false);
    typeAndCheck(nonProjectFile2, false);
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile1));
    assertNotNull(NonProjectFileWritingAccessProvider.getAccessStatus(getProject(), nonProjectFile2));
  }

  private List<VirtualFile> registerWriteAccessProvider(final VirtualFile... filesToDeny) {
    final List<VirtualFile> requested = new ArrayList<VirtualFile>();
    PlatformTestUtil.registerExtension(Extensions.getArea(getProject()), WritingAccessProvider.EP_NAME, new WritingAccessProvider() {
      @NotNull
      @Override
      public Collection<VirtualFile> requestWriting(VirtualFile... files) {
        Collections.addAll(requested, files);
        HashSet<VirtualFile> denied = new HashSet<VirtualFile>(Arrays.asList(filesToDeny));
        denied.retainAll(Arrays.asList(files));
        return denied;
      }

      @Override
      public boolean isPotentiallyWritable(@NotNull VirtualFile file) {
        return true;
      }
    }, myTestRootDisposable);
    return requested;
  }

  @NotNull
  private VirtualFile createProjectFile() {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        result.setResult(ModuleRootManager.getInstance(myModule).getContentRoots()[0].createChildData(this, "projectFile.txt"));
      }
    }.execute().getResultObject();
  }

  @NotNull
  private VirtualFile createNonProjectFile() {
    return new WriteAction<VirtualFile>() {
      @Override
      protected void run(@NotNull Result<VirtualFile> result) throws Throwable {
        VirtualFile tmp = LocalFileSystem.getInstance().findFileByIoFile(FileUtil.createTempDirectory("tmp", null));
        result.setResult(tmp.createChildData(this, "nonProjectFile.txt"));
      }
    }.execute().getResultObject();
  }

  private NonProjectFileNotificationPanel typeAndCheck(VirtualFile file, boolean changed) {
    return typeAndCheck(file, changed, !changed);
  }

  private NonProjectFileNotificationPanel typeAndCheck(VirtualFile file, boolean changed, boolean hasWarningPanel) {
    Editor editor = getEditor(file);

    String before = editor.getDocument().getText();
    typeInChar(editor, 'a');

    NonProjectFileNotificationPanel panel = getNotificationPanel(file);
    if (changed) {
      assertEquals("Text should be changed", 'a' + before, editor.getDocument().getText());
    }
    else {
      assertEquals("Text should not be changed", before, editor.getDocument().getText());
    }
    assertEquals(hasWarningPanel, panel != null);
    return panel;
  }

  private NonProjectFileNotificationPanel getNotificationPanel(VirtualFile file) {
    Editor editor = getEditor(file);

    FileEditorManagerImpl manager = (FileEditorManagerImpl)FileEditorManager.getInstance(getProject());
    List<JComponent> topComponents = manager.getTopComponents(getFileEditor(editor));
    if (topComponents.isEmpty()) return null;

    JComponent panel = topComponents.get(0);
    assertTrue(panel instanceof NonProjectFileNotificationPanel);
    return (NonProjectFileNotificationPanel)panel;
  }

  protected FileEditor getFileEditor(Editor e) {
    return e == null ? null : TextEditorProvider.getInstance().getTextEditor(e);
  }

  private Editor getEditor(VirtualFile file) {
    return FileEditorManager.getInstance(getProject()).openTextEditor(new OpenFileDescriptor(getProject(), file, 0), false);
  }

  protected void typeInChar(Editor e, char c) {
    getActionManager().getTypedAction().actionPerformed(e, c, createDataContextFor(e));
  }

  private DataContext createDataContextFor(final Editor editor) {
    return new DataContext() {
      @Override
      public Object getData(String dataId) {
        if (dataId.equals(DataConstants.EDITOR)) return editor;
        if (dataId.equals(DataConstants.PROJECT)) return getProject();
        return null;
      }
    };
  }

  private static EditorActionManager getActionManager() {
    return EditorActionManager.getInstance();
  }
}
