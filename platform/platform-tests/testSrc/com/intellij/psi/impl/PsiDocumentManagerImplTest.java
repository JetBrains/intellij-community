// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.mock.MockDocument;
import com.intellij.mock.MockPsiFile;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable;
import com.intellij.openapi.editor.ex.PrioritizedDocumentListener;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileTooBigException;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import com.intellij.openapi.vfs.limits.FileSizeLimit;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.TestTimeOut;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;
import org.junit.Assume;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PsiDocumentManagerImplTest extends HeavyPlatformTestCase {
  private static final int TIMEOUT_MS = 30_000;

  @Override
  protected void tearDown() throws Exception {
    try {
      LaterInvocator.leaveAllModals();
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private PsiDocumentManagerImpl getPsiDocumentManager() {
    return (PsiDocumentManagerImpl)PsiDocumentManager.getInstance(getProject());
  }

  public void testGetCachedPsiFile_NoFile() {
    final PsiFile file = getPsiDocumentManager().getCachedPsiFile(new MockDocument());
    assertNull(file);
  }

  public void testGetPsiFile_NotRegisteredDocument() {
    final PsiFile file = getPsiDocumentManager().getPsiFile(new MockDocument());
    assertNull(file);
  }

  public void testGetDocument_FirstGet() {
    VirtualFile vFile = createFile();
    final PsiFile file = new MockPsiFile(vFile, getPsiManager());

    final Document document = getDocument(file);
    assertNotNull(document);
    assertSame(document, FileDocumentManager.getInstance().getDocument(vFile));
  }

  private static LightVirtualFile createFile() {
    return new LightVirtualFile("foo.txt");
  }

  public void testDocumentGced() throws IOException {
    VirtualFile vFile = createTempVirtualFile("x.txt", null, "abc", StandardCharsets.UTF_8);
    PsiDocumentManagerImpl documentManager = getPsiDocumentManager();
    long id = System.identityHashCode(documentManager.getDocument(findFile(vFile)));

    documentManager.commitAllDocuments();
    UIUtil.dispatchAllInvocationEvents();
    assertEmpty(documentManager.getUncommittedDocuments());

    LeakHunter.checkLeak(documentManager, DocumentImpl.class, doc -> id == System.identityHashCode(doc));
    LeakHunter.checkLeak(documentManager, PsiFileImpl.class, psiFile -> vFile.equals(psiFile.getVirtualFile()));

    GCWatcher.tracking(documentManager.getCachedDocument(findFile(vFile))).ensureCollected();
    assertNull(documentManager.getCachedDocument(findFile(vFile)));

    Document newDoc = documentManager.getDocument(findFile(vFile));
    assertTrue(id != System.identityHashCode(newDoc));
  }

  private PsiFile findFile(@NotNull VirtualFile vFile) {
    return getPsiManager().findFile(vFile);
  }

  public void testGetUncommittedDocuments_noDocuments() {
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentChanged_DontProcessEvents() {
    final PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, () -> PsiToDocumentSynchronizer
      .performAtomically(file, () -> changeDocument(document, getPsiDocumentManager())));


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentNotRegistered() {
    final Document document = new MockDocument();

    WriteCommandAction.runWriteCommandAction(null, () -> changeDocument(document, getPsiDocumentManager()));


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testCommitDocument_RemovesFromUncommittedList() {
    PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, () -> changeDocument(document, getPsiDocumentManager()));

    getPsiDocumentManager().commitDocument(document);
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  private Document getDocument(PsiFile file) {
    return getPsiDocumentManager().getDocument(file);
  }

  private static void changeDocument(Document document, PsiDocumentManagerImpl manager) {
    DocumentEventImpl event = new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false, 0, 0, 0);
    manager.beforeDocumentChange(event);
    manager.documentChanged(event);
  }

  public void testCommitAllDocument_RemovesFromUncommittedList() {
    PsiFile file = findFile(createFile());

    final Document document = getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, () -> changeDocument(document, getPsiDocumentManager()));


    getPsiDocumentManager().commitAllDocuments();
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testDocumentFromAlienProjectDoesNotEndUpInMyUncommittedList() throws Exception {
    PsiFile file = findFile(createFile());

    Document document = getDocument(file);

    Project alienProject = PlatformTestUtil.loadAndOpenProject(createTempDirectory().toPath().resolve("alien.ipr"), getTestRootDisposable());
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
    waitAndPump(semaphore);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(0, "class X {}"));

    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(() -> {
      assertTrue(getPsiDocumentManager().isCommitted(document));
      semaphore.up();
    });
    waitAndPump(semaphore);
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

  public void testDocumentCommittedInBackgroundEventuallyEvenDespiteTyping() throws IOException {
    assumeJavaInClassPath();
    VirtualFile virtualFile = createTempVirtualFile("x.java", null, "", StandardCharsets.UTF_8);
    PsiFile file = findFile(virtualFile);
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getDocument(file);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> document
      .insertString(0, "class X {" + StringUtil.repeat("public int IIII = 222;\n", 10000) + "}"));

    waitForCommits();

    assertEquals(StdFileTypes.JAVA.getLanguage(), file.getLanguage());

    for (int i = 0; i < 30; i++) {
      assertTrue("Still not committed: " + document, getPsiDocumentManager().isCommitted(document));
      WriteCommandAction.runWriteCommandAction(null, () -> {
        document.insertString(0, "/**/");
        assertFalse(getPsiDocumentManager().isCommitted(document));
      });
      waitForCommits();
      WriteCommandAction.runWriteCommandAction(null, () -> document.deleteString(0, "/**/".length()));
      waitForCommits();
      String dumpBefore = ThreadDumper.dumpThreadsToString();
      if (!getPsiDocumentManager().isCommitted(document)) {
        System.err.println("Thread dump1:\n" + dumpBefore + "\n;Thread dump2:\n" + ThreadDumper.dumpThreadsToString());
        fail("Still not committed: " + document);
      }
    }
  }

  private static void waitAndPump(Semaphore semaphore) {
    TestTimeOut t = TestTimeOut.setTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    while (!t.timedOut()) {
      if (semaphore.waitFor(1)) return;
      UIUtil.dispatchAllInvocationEvents();
    }
    fail("Timeout");
  }

  public void testDocumentFromAlienProjectGetsCommittedInBackground() throws Exception {
    VirtualFile virtualFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile file = findFile(virtualFile);

    final Document document = getDocument(file);

    Project alienProject = PlatformTestUtil.loadAndOpenProject(createTempDirectory().toPath().resolve("alien.ipr"), getTestRootDisposable());
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

    waitForCommits();
    assertTrue("Still not committed: " + document, getPsiDocumentManager().isCommitted(document));

    TestTimeOut t = TestTimeOut.setTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    while (!alienDocManager.isCommitted(alienDocument) && !t.timedOut()) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue("Still not committed: " + alienDocument, alienDocManager.isCommitted(alienDocument));
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

    assertInstanceOf(psiFile, PsiLargeTextFile.class);
    assertLargeFileContentLimited(getTooLargeContent(), vFile, document);
  }

  private void makeFileTooLarge(final VirtualFile vFile) throws Exception {
    WriteCommandAction.runWriteCommandAction(myProject, (ThrowableComputable<Object, Exception>)() -> {
      setFileText(vFile, getTooLargeContent());
      PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      return null;
    });
  }

  public void testFileTooLarge() throws IOException {
    String content = getTooLargeContent();
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", content));
    PsiFile psiFile = findFile(vFile);
    Document document = getDocument(psiFile);

    assertNotNull(document);
    assertInstanceOf(psiFile, PsiLargeTextFile.class);

    assertLargeFileContentLimited(content, vFile, document);
  }

  public void testBinaryFileTooLarge() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.zip", getTooLargeContent()));
    PsiFile psiFile = findFile(vFile);
    Document document = getDocument(psiFile);
    assertNull(document);
    assertInstanceOf(psiFile, PsiLargeBinaryFile.class);
  }

  public void testLargeFileException() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", getTooLargeContent()));
    assertThrows(FileTooBigException.class, () -> vFile.getOutputStream(this));
    assertThrows(FileTooBigException.class, () -> vFile.setBinaryContent(new byte[]{}));
    assertThrows(FileTooBigException.class, () -> vFile.setBinaryContent(ArrayUtilRt.EMPTY_BYTE_ARRAY, 1, 2));
    assertThrows(FileTooBigException.class, () -> vFile.setBinaryContent(ArrayUtilRt.EMPTY_BYTE_ARRAY, 1, 2, this));
    assertThrows(FileTooBigException.class, () -> vFile.contentsToByteArray());
    assertThrows(FileTooBigException.class, () -> vFile.contentsToByteArray(false));
  }

  private void assertNoFileDocumentMapping(VirtualFile vFile, PsiFile psiFile, Document document) {
    assertNull(FileDocumentManager.getInstance().getDocument(vFile));
    assertNull(FileDocumentManager.getInstance().getFile(document));
    assertNull(getPsiDocumentManager().getPsiFile(document));
    assertNull(getDocument(psiFile));
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
    assertNotSame(ModalityState.nonModal(), ModalityState.current());

    // may or may not be committed until exit modal dialog
    waitForCommits();

    LaterInvocator.leaveModal(dialog);
    assertEquals(ModalityState.nonModal(), ModalityState.current());

    // must commit
    waitForCommits();
    assertTrue(getPsiDocumentManager().isCommitted(document));

    // check that inside modal dialog commit is possible
    ApplicationManager.getApplication().runWriteAction(() -> {
      // commit thread is paused

      LaterInvocator.enterModal(dialog);
      document.setText("yyy");
    });
    assertNotSame(ModalityState.nonModal(), ModalityState.current());

    // must commit
    waitForCommits();

    assertTrue(getPsiDocumentManager().isCommitted(document));
  }

  public void testDoNotAutoCommitIfModalDialogSuddenlyAppears() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = findFile(vFile);
    Document document = getDocument(psiFile);

    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "a"));
    assertFalse(getPsiDocumentManager().isCommitted(document));

    LaterInvocator.enterModal(new Object());

    waitForCommits();
    assertFalse(getPsiDocumentManager().isCommitted(document));

    LaterInvocator.leaveAllModals();
    waitForCommits();
    assertTrue(getPsiDocumentManager().isCommitted(document));
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
    assertNotSame(ModalityState.nonModal(), ModalityState.current());


    // may or may not commit in background by default when modality changed
    waitForCommits();

    // but, when performWhenAllCommitted() in modal context called, should re-add documents into queue nevertheless
    boolean[] calledPerformWhenAllCommitted = new boolean[1];
    getPsiDocumentManager().performWhenAllCommitted(() -> calledPerformWhenAllCommitted[0] = true);

    // must commit now
    waitForCommits();
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertTrue(calledPerformWhenAllCommitted[0]);
  }

  private static void waitForCommits() {
    try {
      DocumentCommitThread.getInstance().waitForAllCommits(100, TimeUnit.SECONDS);
    }
    catch (ExecutionException | InterruptedException | TimeoutException e) {
      throw new RuntimeException(e);
    }
  }

  public void testReparseDoesNotModifyDocument() throws Exception {
    VirtualFile file = createTempVirtualFile("x.txt", null, "1\n2\n3\n", StandardCharsets.UTF_8);
    EditorSettingsExternalizable editorSettings = EditorSettingsExternalizable.getInstance();
    String stripSpacesBefore = editorSettings.getStripTrailingSpaces();
    try {
      editorSettings.setStripTrailingSpaces(EditorSettingsExternalizable.STRIP_TRAILING_SPACES_CHANGED);
      final Document document = FileDocumentManager.getInstance().getDocument(file);
      assertNotNull(document);
      WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(document.getTextLength(), " "));

      PsiDocumentManager.getInstance(myProject).reparseFiles(Collections.singleton(file), false);
      assertEquals("1\n2\n3\n", VfsUtilCore.loadText(file));

      WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "-"));
      FileDocumentManager.getInstance().saveDocument(document);
      assertEquals("-1\n2\n3\n", VfsUtilCore.loadText(file));
    }
    finally {
      editorSettings.setStripTrailingSpaces(stripSpacesBefore);
    }
  }

  public void testPerformWhenAllCommittedMustNotNest() {
    PsiFile file = findFile(createFile());
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getDocument(file);
    assertNotNull(document);

    WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(0, "class X {}"));

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
    assumeJavaInClassPath();
    VirtualFile virtualFile = getVirtualFile(createTempFile("X.java", ""));
    PsiFile file = findFile(virtualFile);
    assertEquals("JAVA", file.getFileType().getName());

    assertNotNull(file);
    assertTrue(file.isPhysical());

    Editor editor = FileEditorManager.getInstance(myProject).openTextEditor(new OpenFileDescriptor(getProject(), virtualFile, 0), false);
    ((EditorImpl)editor).setCaretActive();

    final Document document = editor.getDocument(); //getDocument(file);

    String text = "class X {" + StringUtil.repeat("void fff() {}\n", 1000) +
                  "}";
    WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(0, text));

    for (int i = 0; i < 300; i++) {
      getPsiDocumentManager().commitAllDocuments();
      assertTrue(getPsiDocumentManager().isCommitted(document));

      String insert = "ddfdkjh";
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, insert));

      UIUtil.dispatchAllInvocationEvents();

      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.replaceString(0, insert.length(), ""));

      FileDocumentManager.getInstance().saveDocument(document);

      assertEquals(text, editor.getDocument().getText());

      waitForCommits();
      assertTrue("Still not committed: " + document + i, getPsiDocumentManager().isCommitted(document));
    }
  }

  public void testCommitNonPhysicalPsiWithoutWriteAction() throws IOException {
    assertFalse(ApplicationManager.getApplication().isWriteAccessAllowed());

    PsiFile original = getPsiManager().findFile(getVirtualFile(createTempFile("X.txt", "")));
    assertNotNull(original);
    assertTrue(original.getViewProvider().isEventSystemEnabled());

    long modCount = getPsiManager().getModificationTracker().getModificationCount();

    PsiFile copy = (PsiFile)original.copy();
    assertFalse(copy.getViewProvider().isEventSystemEnabled());

    Document document = copy.getViewProvider().getDocument();
    assertNotNull(document);
    document.setText("class A{}");

    PsiDocumentManager.getInstance(myProject).commitDocument(document);
    assertEquals(modCount, getPsiManager().getModificationTracker().getModificationCount());
    assertEquals(document.getText(), copy.getText());
    assertTrue(PsiDocumentManager.getInstance(myProject).isCommitted(document));
  }

  public void testPerformWhenAllCommittedWorksAfterFileDeletion() throws Exception {
    PsiFile file = getPsiManager().findFile(getVirtualFile(createTempFile("X.txt", "")));
    Document document = file.getViewProvider().getDocument();
    assertNotNull(document);

    AtomicBoolean invoked = new AtomicBoolean();
    WriteAction.run(() -> {
      document.setText("class A{}");
      PsiDocumentManager.getInstance(myProject).performWhenAllCommitted(() -> invoked.set(true));
      file.getVirtualFile().delete(this);
    });
    PsiDocumentManager.getInstance(myProject).commitAllDocuments();
    assertTrue(invoked.get());
  }

  public void testPerformLaterWhenAllCommittedFromCommitHandler() throws Exception {
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "", 0, true);
    Document document = file.getViewProvider().getDocument();

    PsiDocumentManager pdm = PsiDocumentManager.getInstance(myProject);
    WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(0, "a"));
    pdm.performWhenAllCommitted(
      () -> pdm.performLaterWhenAllCommitted(
        () -> WriteCommandAction.runWriteCommandAction(null, () -> document.insertString(1, "b"))));

    assertTrue(pdm.hasUncommitedDocuments());
    assertEquals("a", document.getText());

    DocumentCommitThread.getInstance().waitForAllCommits(100, TimeUnit.SECONDS);
    assertEquals("ab", document.getText());
  }

  public void testBackgroundCommitDoesNotChokeByWildChangesWhichInvalidatePsiFile() throws Exception {
    assumeJavaInClassPath();
    @Language("JAVA")
    String text = "\n\nclass X {\npublic static final String string =null;\n public void x() {}\n}";
    VirtualFile virtualFile = getVirtualFile(createTempFile("X.java", text));
    PsiFile file = getPsiManager().findFile(virtualFile);
    DocumentEx document = (DocumentEx)file.getViewProvider().getDocument();
    PsiDocumentManager pdm = PsiDocumentManager.getInstance(myProject);
    pdm.commitAllDocuments();

    WriteCommandAction.runWriteCommandAction(null, () -> {
      try {
        virtualFile.setBinaryContent("\n txt txt txt".getBytes(StandardCharsets.UTF_8));
        virtualFile.rename(this, "X.txt");
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    DocumentCommitThread.getInstance().waitForAllCommits(100, TimeUnit.SECONDS);

    assertTrue(pdm.isCommitted(document));
    assertFalse(file.isValid());

    WriteCommandAction.runWriteCommandAction(null, () ->
      document.replaceString(0, document.getTextLength(), "xxxxxxxxxxxxxxxxxxxx"));
    pdm.commitAllDocuments();
    assertTrue(pdm.isCommitted(document));

    PsiFile file2 = getPsiManager().findFile(virtualFile);
    assertEquals(PlainTextLanguage.INSTANCE, file2.getLanguage());
  }

  public void testNoLeaksAfterPCEInListener() {
    Document document = createFreeThreadedDocument();
    document.addDocumentListener(new PrioritizedDocumentListener() {
      @Override
      public int getPriority() {
        return 0;
      }

      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent event) {
        throw new ProcessCanceledException();
      }
    });
    try {
      document.insertString(0, "a");
      fail("PCE expected");
    }
    catch (ProcessCanceledException ignored) {
    }
    getPsiDocumentManager().commitAllDocuments();
    LeakHunter.checkLeak(getPsiDocumentManager(), Document.class, d -> d == document);
  }

  private Document createFreeThreadedDocument() {
    PsiFile file = PsiFileFactory.getInstance(myProject).createFileFromText("a.txt", PlainTextFileType.INSTANCE, "");
    return file.getViewProvider().getDocument();
  }

  private void assertLargeFileContentLimited(@NotNull String content, @NotNull VirtualFile vFile, @NotNull Document document) {
    Charset charset = EncodingProjectManager.getInstance(getProject()).getEncoding(vFile, false);
    float bytesPerChar = charset == null ? 2 : charset.newEncoder().averageBytesPerChar();
    int contentSize = (int)(FileSizeLimit.getPreviewLimit(vFile.getExtension()) / bytesPerChar);
    String substring = content.substring(0, contentSize);
    assertEquals(substring, document.getText());
  }

  @NotNull
  private static String getTooLargeContent() {
    return StringUtil.repeat("a", FileSizeLimit.getDefaultContentLoadLimit() + 1);
  }

  public void testDefaultProjectDocumentsAreAutoCommitted() throws IOException {
    assumeJavaInClassPath();
    Project defaultProject = ProjectManager.getInstance().getDefaultProject();
    VirtualFile vFile = getVirtualFile(createTempFile("a.java", ""));
    PsiFile psiFile = PsiManager.getInstance(defaultProject).findFile(vFile);
    PsiDocumentManager documentManager = PsiDocumentManager.getInstance(defaultProject);
    Document document = documentManager.getDocument(psiFile);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText("// things"));
    waitForCommits();
    assertTrue(documentManager.isCommitted(document));
    PsiElement firstChild = psiFile.getFirstChild();
    assertTrue(firstChild instanceof PsiComment);
  }

  private static void assumeJavaInClassPath() {
    Assume.assumeTrue("This test must have JavaFileType in its classpath", FileTypeManager.getInstance().findFileTypeByName("JAVA") != null);
  }

  public void testAutoCommitDoesNotGetStuckForDocumentsWithIgnoredFileName() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt~", "text"));
    assertNotNull(getPsiManager().findViewProvider(vFile));
    assertNull(getPsiManager().findFile(vFile)); // because it's ignored

    Document document = FileDocumentManager.getInstance().getDocument(vFile);
    ApplicationManager.getApplication().runWriteAction(() -> document.setText("// things"));

    boolean[] calledPerformWhenAllCommitted = new boolean[1];
    getPsiDocumentManager().performWhenAllCommitted(() -> calledPerformWhenAllCommitted[0] = true);
    waitForCommits();

    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertTrue(calledPerformWhenAllCommitted[0]);
  }

  public void testNonPhysicalDocumentCommitsDoNotInterruptBackgroundTasks() {
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
    List<PsiFile> files = new ArrayList<>();
    for (int i = 0; i < 90; i++) {
      files.add(factory.createFileFromText("a.xml", XMLLanguage.INSTANCE, "<a><b><c/></b></a>", false, false));
    }

    AtomicInteger attempts = new AtomicInteger();
    CancellablePromise<Void> future = ReadAction.nonBlocking(() -> {
      attempts.incrementAndGet();

      for (PsiFile file : files) {
        TimeoutUtil.sleep(1);

        Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());
        document.insertString(0, " ");

        for (PsiElement element : SyntaxTraverser.psiTraverser(file)) {
          ProgressManager.checkCanceled();
          assertNotNull(element.getTextRange());
        }
      }
    }).submit(AppExecutorUtil.getAppExecutorService());
    PlatformTestUtil.waitForFuture(future, 10_000);

    assertTrue(String.valueOf(attempts), attempts.get() < 10);
  }

  public void testAllowCommittingNonPhysicalDocumentsInBackgroundThread() throws Exception {
    PsiDocumentManagerImpl pdm = getPsiDocumentManager();
    ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
      String text = "text";
      PsiFile file =
        PsiFileFactory.getInstance(getProject()).createFileFromText("a.txt", PlainTextLanguage.INSTANCE, text, false, false);
      Document document = FileDocumentManager.getInstance().getDocument(file.getViewProvider().getVirtualFile());

      AtomicBoolean documentCommitCallback = new AtomicBoolean();
      pdm.performForCommittedDocument(document, () -> documentCommitCallback.set(true));
      assertTrue(documentCommitCallback.getAndSet(false));

      document.insertString(0, " ");
      assertEquals(text, file.getText());

      pdm.performForCommittedDocument(document, () -> documentCommitCallback.set(true));
      assertFalse(documentCommitCallback.get());

      pdm.commitDocument(document);
      assertEquals(" " + text, file.getText());
      assertTrue(documentCommitCallback.get());
    })).get();
  }

  public void testPerformWhenAllCommittedDoesNotRaceWithBackgroundLightCommitsResultingInExceptions(){
    ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(getTestName(false), 10);

    PsiFile mainFile = findFile(createFile());
    Document mainDoc = getDocument(mainFile);

    PsiFileFactory factory = PsiFileFactory.getInstance(getProject());
    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      for (int j = 0; j < 20; j++) {
        PsiFile tempFile = factory.createFileFromText(i + ".xml", XMLLanguage.INSTANCE, "<a><b><c/></b></a>", false, false);
        Document document = FileDocumentManager.getInstance().getDocument(tempFile.getViewProvider().getVirtualFile());
        document.insertString(0, " ");

        futures.add(ReadAction.nonBlocking(() -> {
          getPsiDocumentManager().commitDocument(document);
          assertEquals(tempFile.getText(), document.getText());
        }).submit(executor));
      }

      Semaphore semaphore = new Semaphore(1);
      WriteCommandAction.runWriteCommandAction(myProject, () -> {
        mainDoc.insertString(0, " ");
        getPsiDocumentManager().performWhenAllCommitted(semaphore::up);
      });
      waitAndPump(semaphore);
      assertTrue(getPsiDocumentManager().isCommitted(mainDoc));
      assertEquals(mainFile.getText(), mainDoc.getText());
    }
    for (Future<?> future : futures) {
      PlatformTestUtil.waitForFuture(future, 10_000);
    }
  }

  public void testDoNotLeakForgottenUncommittedDocument() throws Exception {
    ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.compute(() -> {
      Document document = createFreeThreadedDocument();
      document.insertString(0, " ");
      assertTrue(getPsiDocumentManager().isUncommited(document));
      assertSameElements(getPsiDocumentManager().getUncommittedDocuments(), document);
      return GCWatcher.tracking(document);
    })).get().ensureCollected();

    assertEmpty(getPsiDocumentManager().getUncommittedDocuments());
  }

  public void testDocumentIsUncommittedInsidePsiListener() {
    PsiFile file = findFile(createFile());
    Document document = getDocument(file);

    AtomicBoolean called = new AtomicBoolean(true);

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(PsiManagerImpl.ANY_PSI_CHANGE_TOPIC, new AnyPsiChangeListener() {
      @Override
      public void afterPsiChanged(boolean isPhysical) {
        called.set(true);
        assertFalse(getPsiDocumentManager().isCommitted(document));
        assertTrue(getPsiDocumentManager().isUncommited(document));
        assertSameElements(getPsiDocumentManager().getUncommittedDocuments(), document);
      }
    });

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(0, "a");
      getPsiDocumentManager().commitDocument(document);
    });
    assertTrue(called.get());
  }

  public void test_performWhenAllCommitted_works_eventually_despite_nonPhysical_uncommitted() {
    Document ftDocument = createFreeThreadedDocument();
    CompletableFuture<Boolean> called = new CompletableFuture<>();
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      ftDocument.insertString(0, " ");
      getPsiDocumentManager().performWhenAllCommitted(() -> called.complete(true));
    });
    assertTrue(PlatformTestUtil.waitForFuture(called, 10_000));
  }

  public void test_performLaterWhenAllCommitted_works_eventually_despite_nonPhysical_uncommitted() {
    Document ftDocument = createFreeThreadedDocument();
    CompletableFuture<Boolean> called = new CompletableFuture<>();
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      ftDocument.insertString(0, " ");
      getPsiDocumentManager().performLaterWhenAllCommitted(() -> called.complete(true));
    });
    assertTrue(PlatformTestUtil.waitForFuture(called, 10_000));
  }

  public void test_AppUIExecutor_withDocumentsCommitted_works_eventually_despite_nonPhysical_uncommitted() {
    Document ftDocument = createFreeThreadedDocument();
    CompletableFuture<Boolean> called = new CompletableFuture<>();
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      ftDocument.insertString(0, " ");
      AppUIExecutor.onUiThread().withDocumentsCommitted(myProject).submit(() -> called.complete(true));
    });
    assertTrue(PlatformTestUtil.waitForFuture(called, 10_000));
  }

  public void test_performWhenAllCommitted_may_be_invoked_from_writeUnsafe_modality() {
    Document document = getDocument(findFile(createFile()));

    CompletableFuture<Boolean> called = new CompletableFuture<>();

    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(0, " ");

      ApplicationManager.getApplication().invokeLater(() -> {
        LaterInvocator.enterModal(this);
        assertFalse(TransactionGuard.getInstance().isWriteSafeModality(ModalityState.defaultModalityState()));
        getPsiDocumentManager().performWhenAllCommitted(() -> called.complete(true));
        waitForCommits();
        assertFalse(called.isDone());
      }, ModalityState.any());
    });

    UIUtil.dispatchAllInvocationEvents();

    LaterInvocator.leaveModal(this);

    assertTrue(PlatformTestUtil.waitForFuture(called, 10_000));
  }

  public void test_commitAndRunReadAction_commits_documents_in_needed_modality() {
    Document document = getDocument(findFile(createFile()));
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, " "));
    ProgressManager.getInstance().run(new Task.Modal(myProject, "", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        assertFalse(getPsiDocumentManager().isCommitted(document));
        getPsiDocumentManager().commitAndRunReadAction(() -> assertTrue(getPsiDocumentManager().isCommitted(document)));
      }
    });
  }
}