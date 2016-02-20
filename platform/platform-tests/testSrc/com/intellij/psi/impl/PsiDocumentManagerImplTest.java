/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.mock.MockDocument;
import com.intellij.mock.MockPsiFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.TrailingSpacesStripper;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiDocumentManagerImplTest extends PlatformTestCase {
  private static final int TIMEOUT = 30000;

  private PsiDocumentManagerImpl getPsiDocumentManager() {
    return (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    DocumentCommitThread.getInstance();
    UIUtil.dispatchAllInvocationEvents();
  }

  @Override
  protected void tearDown() throws Exception {
    Object[] entities = LaterInvocator.getCurrentModalEntities();
    for (int i = entities.length - 1; i >= 0; i--) {
      Object state = entities[i];
      LaterInvocator.leaveModal(state);
    }
    super.tearDown();
  }

  public void testGetCachedPsiFile_NoFile() throws Exception {
    final PsiFile file = getPsiDocumentManager().getCachedPsiFile(new MockDocument());
    assertNull(file);
  }

  public void testGetPsiFile_NotRegisteredDocument() throws Exception {
    final PsiFile file = getPsiDocumentManager().getPsiFile(new MockDocument());
    assertNull(file);
  }

  public void testGetDocument_FirstGet() throws Exception {
    VirtualFile vFile = createFile();
    final PsiFile file = new MockPsiFile(vFile, getPsiManager());

    final Document document = getDocument(file);
    assertNotNull(document);
    assertSame(document, FileDocumentManager.getInstance().getDocument(vFile));
  }

  private static LightVirtualFile createFile() {
    return new LightVirtualFile("foo.txt");
  }

  public void testDocumentGced() throws Exception {
    VirtualFile vFile = getVirtualFile(createTempFile("txt", "abc"));
    PsiDocumentManagerImpl documentManager = getPsiDocumentManager();
    long id = System.identityHashCode(documentManager.getDocument(findFile(vFile)));

    documentManager.commitAllDocuments();
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.dispatchAllInvocationEvents();
    assertEmpty(documentManager.getUncommittedDocuments());

    LeakHunter.checkLeak(documentManager, DocumentImpl.class);
    LeakHunter.checkLeak(documentManager, PsiFileImpl.class,
                         psiFile -> psiFile.getViewProvider().getVirtualFile().getFileSystem() instanceof LocalFileSystem);

    for (int i = 0; i < 1000; i++) {
      PlatformTestUtil.tryGcSoftlyReachableObjects();
      UIUtil.dispatchAllInvocationEvents();
      if (documentManager.getCachedDocument(findFile(vFile)) == null) break;
      System.gc();
    }
    assertNull(documentManager.getCachedDocument(findFile(vFile)));

    Document newDoc = documentManager.getDocument(findFile(vFile));
    assertTrue(id != System.identityHashCode(newDoc));
  }

  private PsiFile findFile(@NotNull VirtualFile vFile) {
    return getPsiManager().findFile(vFile);
  }

  public void testGetUncommittedDocuments_noDocuments() throws Exception {
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentChanged_DontProcessEvents() throws Exception {
    final PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      getPsiDocumentManager().getSynchronizer().performAtomically(file, () -> changeDocument(document, getPsiDocumentManager()));
    });


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentNotRegistered() throws Exception {
    final Document document = new MockDocument();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      changeDocument(document, getPsiDocumentManager());
    });


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testCommitDocument_RemovesFromUncommittedList() throws Exception {
    PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      changeDocument(document, getPsiDocumentManager());
    });

    getPsiDocumentManager().commitDocument(document);
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  private Document getDocument(PsiFile file) {
    return getPsiDocumentManager().getDocument(file);
  }

  private static void changeDocument(Document document, PsiDocumentManagerImpl manager) {
    DocumentEventImpl event = new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false);
    manager.beforeDocumentChange(event);
    manager.documentChanged(event);
  }

  public void testCommitAllDocument_RemovesFromUncommittedList() throws Exception {
    PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      changeDocument(document, getPsiDocumentManager());
    });


    getPsiDocumentManager().commitAllDocuments();
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testDocumentFromAlienProjectDoesNotEndUpInMyUncommittedList() throws Exception {
    PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    File temp = createTempDirectory();
    final Project alienProject = createProject(temp + "/alien.ipr", DebugUtil.currentStackTrace());
    boolean succ2 = ProjectManagerEx.getInstanceEx().openProject(alienProject);
    assertTrue(succ2);
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    try {
      PsiManager alienManager = PsiManager.getInstance(alienProject);
      final String alienText = "alien";

      LightVirtualFile alienVirt = new LightVirtualFile("foo.txt", alienText);
      final PsiFile alienFile = alienManager.findFile(alienVirt);
      final PsiDocumentManagerImpl alienDocManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(alienProject);
      final Document alienDocument = alienDocManager.getDocument(alienFile);
      assertEquals(0, alienDocManager.getUncommittedDocuments().length);
      assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);

      WriteCommandAction.runWriteCommandAction(null, () -> {
        changeDocument(alienDocument, getPsiDocumentManager());
        assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
        assertEquals(0, alienDocManager.getUncommittedDocuments().length);

        changeDocument(alienDocument, alienDocManager);
        assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
        assertEquals(1, alienDocManager.getUncommittedDocuments().length);

        changeDocument(document, getPsiDocumentManager());
        assertEquals(1, getPsiDocumentManager().getUncommittedDocuments().length);
        assertEquals(1, alienDocManager.getUncommittedDocuments().length);

        changeDocument(document, alienDocManager);
        assertEquals(1, getPsiDocumentManager().getUncommittedDocuments().length);
        assertEquals(1, alienDocManager.getUncommittedDocuments().length);
      });
    }
    finally {
      ProjectUtil.closeAndDispose(alienProject);
    }
  }

  public void testCommitInBackground() {
    PsiFile file = findFile(createFile());
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getDocument(file);
    assertNotNull(document);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(() -> {
      assertTrue(getPsiDocumentManager().isCommitted(document));
      semaphore.up();
    });
    waitAndPump(semaphore, TIMEOUT);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(0, "class X {}");
    });

    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(() -> {
      assertTrue(getPsiDocumentManager().isCommitted(document));
      semaphore.up();
    });
    waitAndPump(semaphore, TIMEOUT);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    final AtomicInteger count = new AtomicInteger();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(0, "/**/");
      boolean executed = getPsiDocumentManager().cancelAndRunWhenAllCommitted("xxx", count::incrementAndGet);
      assertFalse(executed);
      executed = getPsiDocumentManager().cancelAndRunWhenAllCommitted("xxx", count::incrementAndGet);
      assertFalse(executed);
      assertEquals(0, count.get());
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertEquals(1, count.get());

    count.set(0);
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(0, "/**/");
      boolean executed = getPsiDocumentManager().performWhenAllCommitted(count::incrementAndGet);
      assertFalse(executed);
      executed = getPsiDocumentManager().performWhenAllCommitted(count::incrementAndGet);
      assertFalse(executed);
      assertEquals(0, count.get());
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertEquals(2, count.get());
  }

  public void testDocumentCommittedInBackgroundEventuallyEvenDespiteTyping() throws InterruptedException, IOException {
    VirtualFile virtualFile = getVirtualFile(createTempFile("X.java", ""));
    PsiFile file = findFile(virtualFile);
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getDocument(file);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(0, "class X {"+StringUtil.repeat("public int IIII = 222;\n",10000)+"}");
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }

    assertEquals(StdFileTypes.JAVA.getLanguage(), file.getLanguage());

    for (int i=0;i<300;i++) {
      assertTrue("Still not committed: " + document, getPsiDocumentManager().isCommitted(document));
      WriteCommandAction.runWriteCommandAction(null, () -> {
        document.insertString(0, "/**/");
        assertFalse(getPsiDocumentManager().isCommitted(document));
      });
      waitForCommit(document, i);
      WriteCommandAction.runWriteCommandAction(null, () -> {
        document.deleteString(0, "/**/".length());
      });
      waitTenSecondsForCommit(document);
      assertTrue("Still not committed: " + document, getPsiDocumentManager().isCommitted(document));
      //System.out.println("i = " + i);
    }
  }

  private static void waitAndPump(Semaphore semaphore, int timeout) {
    final long limit = System.currentTimeMillis() + timeout;
    while (System.currentTimeMillis() < limit) {
      if (semaphore.waitFor(1)) return;
      UIUtil.dispatchAllInvocationEvents();
    }
    fail("Timeout");
  }

  public void testDocumentFromAlienProjectGetsCommittedInBackground() throws Exception {
    LightVirtualFile virtualFile = createFile();
    PsiFile file = findFile(virtualFile);

    final Document document = getDocument(file);

    File temp = createTempDirectory();
    final Project alienProject = createProject(temp + "/alien.ipr", DebugUtil.currentStackTrace());
    boolean succ2 = ProjectManagerEx.getInstanceEx().openProject(alienProject);
    assertTrue(succ2);
    UIUtil.dispatchAllInvocationEvents(); // startup activities

    try {
      PsiManager alienManager = PsiManager.getInstance(alienProject);

      final PsiFile alienFile = alienManager.findFile(virtualFile);
      assertNotNull(alienFile);
      final PsiDocumentManagerImpl alienDocManager = (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(alienProject);
      final Document alienDocument = alienDocManager.getDocument(alienFile);
      assertSame(document, alienDocument);
      assertEmpty(alienDocManager.getUncommittedDocuments());
      assertEmpty(getPsiDocumentManager().getUncommittedDocuments());

      WriteCommandAction.runWriteCommandAction(null, () -> {
        document.setText("xxx");
        assertOrderedEquals(getPsiDocumentManager().getUncommittedDocuments(), document);
        assertOrderedEquals(alienDocManager.getUncommittedDocuments(), alienDocument);
      });
      assertEquals("xxx", document.getText());
      assertEquals("xxx", alienDocument.getText());

      waitForCommit(document, TIMEOUT);
      assertTrue("Still not committed: " + document, getPsiDocumentManager().isCommitted(document));

      long t2 = System.currentTimeMillis() + TIMEOUT;
      while (!alienDocManager.isCommitted(alienDocument) && System.currentTimeMillis() < t2) {
        UIUtil.dispatchAllInvocationEvents();
      }
      assertTrue("Still not committed: " + alienDocument, alienDocManager.isCommitted(alienDocument));
    }
    finally {
      ProjectUtil.closeAndDispose(alienProject);
    }
  }

  public void testCommitThreadGetSuspendedDuringWriteActions() {
    final DocumentCommitThread commitThread = DocumentCommitThread.getInstance();
    assertTrue(commitThread.isEnabled());
    WriteCommandAction.runWriteCommandAction(null, () -> {
      if (commitThread.isEnabled()) {
        System.err.println("commitThread: "+commitThread + ";\n"+commitThread.log+";\n"+ThreadDumper.dumpThreadsToString());
      }
      assertFalse(commitThread.isEnabled());
      WriteCommandAction.runWriteCommandAction(null, () -> {
        assertFalse(commitThread.isEnabled());
      });
      assertFalse(commitThread.isEnabled());
    });
    assertTrue(commitThread.isEnabled());
  }

  public void testFileChangesToText() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = findFile(vFile);
    Document document = getDocument(psiFile);

    rename(vFile, "a.xml");
    assertFalse(psiFile.isValid());
    assertNotSame(psiFile, findFile(vFile));
    psiFile = findFile(vFile);

    assertSame(document, FileDocumentManager.getInstance().getDocument(vFile));
    assertSame(document, getDocument(psiFile));
  }

  public void testFileChangesToBinary() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = findFile(vFile);
    Document document = getDocument(psiFile);

    rename(vFile, "a.zip");
    assertFalse(psiFile.isValid());
    psiFile = findFile(vFile);
    assertInstanceOf(psiFile, PsiBinaryFile.class);

    assertNoFileDocumentMapping(vFile, psiFile, document);
    assertEquals("abc", document.getText());
  }

  public void testFileBecomesTooLarge() throws Exception {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = findFile(vFile);
    Document document = getDocument(psiFile);

    makeFileTooLarge(vFile);
    assertFalse(psiFile.isValid());
    psiFile = findFile(vFile);
    assertInstanceOf(psiFile, PsiLargeFile.class);

    assertNoFileDocumentMapping(vFile, psiFile, document);
    assertEquals("abc", document.getText());
  }

  private void assertNoFileDocumentMapping(VirtualFile vFile, PsiFile psiFile, Document document) {
    assertNull(FileDocumentManager.getInstance().getDocument(vFile));
    assertNull(FileDocumentManager.getInstance().getFile(document));
    assertNull(getPsiDocumentManager().getPsiFile(document));
    assertNull(getDocument(psiFile));
  }

  private void makeFileTooLarge(final VirtualFile vFile) throws Exception {
    WriteCommandAction.runWriteCommandAction(myProject, (ThrowableComputable<Object, Exception>)() -> {
      setFileText(vFile, StringUtil.repeat("a", FileUtilRt.LARGE_FOR_CONTENT_LOADING + 1));
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      return null;
    });
  }

  public void testCommitDocumentInModalDialog() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = findFile(vFile);
    final Document document = getDocument(psiFile);

    final DialogWrapper dialog = new DialogWrapper(getProject()) {
      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        return null;
      }
    };

    disposeOnTearDown(() -> dialog.close(DialogWrapper.OK_EXIT_CODE));
    ApplicationManager.getApplication().runWriteAction(() -> {
      // commit thread is paused
      document.setText("xx");

      LaterInvocator.enterModal(dialog);
    });
    assertNotSame(ModalityState.NON_MODAL, ApplicationManager.getApplication().getCurrentModalityState());

    // must not be committed until exit modal dialog
    waitTenSecondsForCommit(document);
    assertFalse(getPsiDocumentManager().isCommitted(document));

    LaterInvocator.leaveModal(dialog);
    assertEquals(ModalityState.NON_MODAL, ApplicationManager.getApplication().getCurrentModalityState());

    // must commit
    waitTenSecondsForCommit(document);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    // check that inside modal dialog commit is possible
    ApplicationManager.getApplication().runWriteAction(() -> {
      // commit thread is paused

      LaterInvocator.enterModal(dialog);
      document.setText("yyy");
    });
    assertNotSame(ModalityState.NON_MODAL, ApplicationManager.getApplication().getCurrentModalityState());

    // must commit
    waitTenSecondsForCommit(document);

    assertTrue(getPsiDocumentManager().isCommitted(document));
    LaterInvocator.leaveModal(dialog);
  }

  public void testChangeDocumentThenEnterModalDialogThenCallPerformWhenAllCommittedShouldFireWhileInsideModal() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = findFile(vFile);
    final Document document = getDocument(psiFile);

    final DialogWrapper dialog = new DialogWrapper(getProject()) {
      @Nullable
      @Override
      protected JComponent createCenterPanel() {
        return null;
      }
    };

    disposeOnTearDown(() -> dialog.close(DialogWrapper.OK_EXIT_CODE));
    ApplicationManager.getApplication().runWriteAction(() -> {
      // commit thread is paused
      document.setText("xx");

      LaterInvocator.enterModal(dialog);
    });
    assertNotSame(ModalityState.NON_MODAL, ApplicationManager.getApplication().getCurrentModalityState());


    // must not commit in background by default when modality changed
    waitTenSecondsForCommit(document);
    assertFalse(getPsiDocumentManager().isCommitted(document));

    // but, when performWhenAllCommitted() in modal context called, should re-add documents into queue nevertheless
    boolean[] calledPerformWhenAllCommitted = new boolean[1];
    getPsiDocumentManager().performWhenAllCommitted(() -> calledPerformWhenAllCommitted[0] = true);

    // must commit now
    waitTenSecondsForCommit(document);
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertTrue(calledPerformWhenAllCommitted[0]);

    LaterInvocator.leaveModal(dialog);
    assertEquals(ModalityState.NON_MODAL, ApplicationManager.getApplication().getCurrentModalityState());
  }

  private void waitTenSecondsForCommit(Document document) {
    waitForCommit(document, 10000);
  }

  private void waitForCommit(Document document, int millis) {
    long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < millis && !getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
  }

  public void testReparseDoesNotModifyDocument() throws Exception {
    VirtualFile file = createTempFile("txt", null, "1\n2\n3\n", Charset.forName("UTF-8"));
    file.putUserData(TrailingSpacesStripper.OVERRIDE_STRIP_TRAILING_SPACES_KEY, EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
    final Document document = FileDocumentManager.getInstance().getDocument(file);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(document.getTextLength(), " ");
    });
    
    PsiDocumentManager.getInstance(myProject).reparseFiles(Collections.singleton(file), false);
    assertEquals("1\n2\n3\n ", VfsUtilCore.loadText(file));

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(0, "-");
    });
    FileDocumentManager.getInstance().saveDocument(document);
    assertEquals("-1\n2\n3\n", VfsUtilCore.loadText(file));
  }

  public void testPerformWhenAllCommittedMustNotNest() {
    PsiFile file = findFile(createFile());
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getDocument(file);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(0, "class X {}");
    });

    getPsiDocumentManager().performWhenAllCommitted(() -> {
      try {
        getPsiDocumentManager().performWhenAllCommitted(() -> {

        });
        fail("Must fail");
      }
      catch (IncorrectOperationException ignored) {
      }
    });
    getPsiDocumentManager().commitAllDocuments();
    assertTrue(getPsiDocumentManager().isCommitted(document));
  }

  public void testUndoShouldAddToCommitQueue() throws IOException {
    VirtualFile virtualFile = getVirtualFile(createTempFile("X.java", ""));
    PsiFile file = findFile(virtualFile);
    assertTrue(file.getFileType().getName().equals("JAVA"));

    assertNotNull(file);
    assertTrue(file.isPhysical());

    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(getProject(), virtualFile, 0), false);
    ((EditorImpl)editor).setCaretActive();

    final Document document = editor.getDocument(); //getDocument(file);

    String text = "class X {" + StringUtil.repeat("void fff() {}\n", 1000) +
               "}";
    WriteCommandAction.runWriteCommandAction(null, () -> {
      document.insertString(0, text);
    });

    for (int i=0;i<300;i++) {
      getPsiDocumentManager().commitAllDocuments();
      assertTrue(getPsiDocumentManager().isCommitted(document));

      String insert = "ddfdkjh";
      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        document.insertString(0, insert);
      });

      TimeoutUtil.sleep(50);

      WriteCommandAction.runWriteCommandAction(getProject(), () -> {
        document.replaceString(0, insert.length(), "");
      });

      FileDocumentManager.getInstance().saveDocument(document);

      assertEquals(text, editor.getDocument().getText());

      waitTenSecondsForCommit(document);
      assertTrue("Still not committed: " + document, getPsiDocumentManager().isCommitted(document));
      System.out.println("i = " + i);
    }
  }
}
