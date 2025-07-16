// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.MemoryDumpHelper;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ref.GCUtil;
import com.intellij.util.ref.GCWatcher;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class FileDocumentManagerImplTest extends HeavyPlatformTestCase {
  private FileDocumentManagerImpl myDocumentManager;
  private Boolean myAskReloadFromDiskResult;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myAskReloadFromDiskResult = null;
    FileDocumentManagerImpl impl = (FileDocumentManagerImpl)FileDocumentManager.getInstance();
    impl.setAskReloadFromDisk(getTestRootDisposable(), new MemoryDiskConflictResolver() {
      @Override
      protected boolean askReloadFromDisk(@NotNull VirtualFile file, @NotNull Document document) {
        if (myAskReloadFromDiskResult == null) {
          fail();
          return false;
        }
        return myAskReloadFromDiskResult.booleanValue();
      }
    });
    myDocumentManager = impl;
  }

  @Override
  protected void tearDown() throws Exception {
    myAskReloadFromDiskResult = null;
    myDocumentManager = null;
    super.tearDown();
  }

  public void testGetCachedDocument_Cached() {
    final Document cachedDocument = myDocumentManager.getCachedDocument(new MockVirtualFile("test.txt"));
    assertNull(cachedDocument);
  }

  public void testGetCachedDocument_NotCached() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);

    assertSame(myDocumentManager.getCachedDocument(file), document);
  }

  public void testGetDocument_CreateNew() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(document);
    assertEquals("test", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(file.isWritable(), document.isWritable());
  }

  public void testGetDocument_CreateNew_ReadOnly() throws Exception {
    final VirtualFile file = createFile();
    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Object, IOException>)() -> {
      file.setWritable(false);
      return null;
    });

    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(document);
    assertEquals("test", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(file.isWritable(), document.isWritable());
  }

  public void testGetDocument_ReturnCachedValueTwice() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(document);
    assertEquals("test", document.getText());

    final Document document2 = myDocumentManager.getDocument(file);
    assertSame(document2, document);
  }

  public void testGetDocument_CreatesNewAfterGCed() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    int idCode = System.identityHashCode(document);
    //noinspection UnusedAssignment
    document = null;

    GCWatcher.tracking(myDocumentManager.getCachedDocument(file)).ensureCollected();

    document = myDocumentManager.getDocument(file);
    assertTrue(idCode != System.identityHashCode(document));
  }

  public void testGetUnsavedDocuments_NoDocuments() {
    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_CreatedDocument() throws Exception {
    final VirtualFile file = createFile();
    myDocumentManager.getDocument(file);

    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_ModifiedDocument() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "xxx"));


    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(1, unsavedDocuments.length);
    assertSame(document, unsavedDocuments[0]);
    assertEquals("test", new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
  }

  public void testGetUnsavedDocuments_afterSaveAllDocuments() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "xxx"));


    myDocumentManager.saveAllDocuments();
    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_afterSaveDocuments() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "xxx"));


    myDocumentManager.saveDocument(document);
    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_afterSaveDocumentWithProblems() throws Exception {
    try {
      final VirtualFile file = new MockVirtualFile("test.txt", "test") {
        @Override
        @NotNull
        public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
          throw new IOException("");
        }
      };

      final Document document = myDocumentManager.getDocument(file);
      assertNotNull(file.toString(), document);
      WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "xxx"));


      try {
        myDocumentManager.saveDocument(document);
        fail("must throw IOException");
      }
      catch (RuntimeException e) {
        assertTrue(e.getCause() instanceof IOException);
      }

      final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
      assertEquals(1, unsavedDocuments.length);
      assertSame(document, unsavedDocuments[0]);
      assertEquals("test", new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
    }
    finally {
      ApplicationManager.getApplication().runWriteAction(() -> myDocumentManager.dropAllUnsavedDocuments());
    }
  }

  public void testUnsavedDocument_DoNotGC() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    int idCode = System.identityHashCode(document);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject,
                                             () -> Objects.requireNonNull(myDocumentManager.getDocument(file)).insertString(0, "xxx"));

    //noinspection UnusedAssignment
    document = null;

    GCUtil.tryGcSoftlyReachableObjects();

    document = myDocumentManager.getDocument(file);
    assertEquals(idCode, System.identityHashCode(document));
  }

  public void testUnsavedDocument_GcAfterSave() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject,
                                             () -> Objects.requireNonNull(myDocumentManager.getDocument(file)).insertString(0, "xxx"));

    //noinspection UnusedAssignment
    document = null;

    myDocumentManager.saveAllDocuments();
    UIUtil.dispatchAllInvocationEvents();
    // "Actions on save" manager retains documents to be saved to run some actions on them
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() -> {
      ActionsOnSaveManager.Companion.getInstance(myProject).waitForTasks();
    });
    PlatformTestUtil.waitWithEventsDispatching("Could not finish auto-correction in 10 seconds", () -> future.isDone(), 10);

    GCWatcher.tracking(myDocumentManager.getDocument(file)).ensureCollected();

    assertNull(myDocumentManager.getCachedDocument(file));
  }

  public void testSaveDocument_DocumentWasNotChanged() throws Exception {
    final VirtualFile file = createFile();
    final long stamp = file.getModificationStamp();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    myDocumentManager.saveDocument(document);
    assertEquals(stamp, file.getModificationStamp());
  }

  public void testSaveDocument_DocumentWasChanged() throws Exception {
    final VirtualFile file = createFile();
    final long stamp = file.getModificationStamp();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "xxx "));


    myDocumentManager.saveDocument(document);
    assertTrue(stamp != file.getModificationStamp());
    assertEquals(document.getModificationStamp(), file.getModificationStamp());
    assertEquals("xxx test", new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
  }

  public void testSaveAllDocuments_DocumentWasChanged() throws Exception {
    final VirtualFile file = createFile();
    final long stamp = file.getModificationStamp();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "xxx "));

    myDocumentManager.saveAllDocuments();
    Assert.assertNotEquals(stamp, file.getModificationStamp());
    assertEquals("xxx test", new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
  }

  public void testGetFile() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    assertSame(file, myDocumentManager.getFile(document));
  }

  public void testConvertSeparators() {
    final VirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    assertEquals("test\ntest", document.getText());
  }

  public void testRememberSeparators() throws Exception {
    final VirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "xxx "));

    myDocumentManager.saveAllDocuments();
    assertEquals("xxx test\rtest", new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
  }

  public void testContentChanged_noDocument() throws Exception {
    final VirtualFile file = createFile();
    setFileText(file, "xxx");
    assertNull(myDocumentManager.getCachedDocument(file));
  }

  private VirtualFile createFile(String name, String content) throws IOException {
    File file = createTempFile(name, content);
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    assertNotNull(virtualFile);
    return virtualFile;
  }
  private VirtualFile createFile() throws IOException {
    return createFile("test.txt", "test");
  }

  public void testContentChanged_documentPresent() throws Exception {
    VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    setFileText(file, "xxx");
    assertNotNull(file.toString(), document);
    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
  }

  public void testContentChanged_ignoreEventsFromSelf() throws Exception {
    final VirtualFile file = createFile("test.txt", "test\rtest");
    Document document = myDocumentManager.getDocument(file);
    setBinaryContent(file, "xxx".getBytes(StandardCharsets.UTF_8), -1, -1, myDocumentManager);

    assertNotNull(file.toString(), document);
    assertEquals("test\ntest", document.getText());
  }

  public void testContentChanged_ignoreEventsFromSelfOnSave() {
    final VirtualFile file = new MockVirtualFile("test.txt", "test\rtest") {
      @NotNull
      @Override
      public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, long newTimeStamp) {
        final VirtualFile self = this;
        return new ByteArrayOutputStream() {
          @Override
          public void close() throws IOException {
            super.close();
            long oldStamp = getModificationStamp();
            setModificationStamp(newModificationStamp);
            setText(toString());
            myDocumentManager.contentsChanged(new VFileContentChangeEvent(null, self, oldStamp, getModificationStamp()));
          }
        };
      }
    };
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "xxx"));

    final long stamp = document.getModificationStamp();

    myDocumentManager.saveAllDocuments();
    assertEquals(stamp, document.getModificationStamp());
  }

  public void testContentChanged_reloadChangedDocument() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "zzz"));


    myAskReloadFromDiskResult = Boolean.TRUE;
    setFileText(file, "xxx");
    UIUtil.dispatchAllInvocationEvents();

    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(0, myDocumentManager.getUnsavedDocuments().length);
  }

  public void testContentChanged_DoNotReloadChangedDocument() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "old "));

    myAskReloadFromDiskResult = Boolean.FALSE;
    long oldDocumentStamp = document.getModificationStamp();

    setBinaryContent(file, "xxx".getBytes(StandardCharsets.UTF_8));
    UIUtil.dispatchAllInvocationEvents();

    assertEquals("old test", document.getText());
    assertEquals(oldDocumentStamp, document.getModificationStamp());
  }

  public void testSaveDocument_DoNotSaveIfModStampEqualsToFile() throws Exception {
    final VirtualFile file = createFile();
    final DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      document.insertString(0, "zzz");
      document.setModificationStamp(file.getModificationStamp());
    });

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeDocumentSaving(@NotNull Document documentToSave) {
        assertNotSame(document, documentToSave);
      }
    });

    myDocumentManager.saveDocument(document);
  }

  // this test requires changes in idea code to support MockFile as local file system file (FileDocumentManager.needsRefresh).
  // TODO: think how to test this functionality without hacking production code
  @SuppressWarnings("UnusedDeclaration")
  public void _testContentChanged_reloadChangedDocumentOnSave() {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test\rtest") {
      @Override
      public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        long oldStamp = getModificationStamp();
        setModificationStamp(LocalTimeCounter.currentTime());
        myDocumentManager.contentsChanged(new VFileContentChangeEvent(null, this, oldStamp, getModificationStamp()));
      }
    };
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "zzz");
    file.setContent(null, "xxx", false);

    myAskReloadFromDiskResult = Boolean.TRUE;
    myDocumentManager.saveAllDocuments();
    long fileStamp = file.getModificationStamp();

    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(file.getModificationStamp(), fileStamp);
    assertEquals(0, myDocumentManager.getUnsavedDocuments().length);
  }

  public void testContentChanged_doNotReloadChangedDocumentOnSave() {
    final MockVirtualFile file =
    new MockVirtualFile("test.txt", "test") {
      @Override
      public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        long oldStamp = getModificationStamp();
        setModificationStamp(LocalTimeCounter.currentTime());
        myDocumentManager.contentsChanged(new VFileContentChangeEvent(null, this, oldStamp, getModificationStamp()));
      }
    };

    myAskReloadFromDiskResult = Boolean.FALSE;
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "old "));

    long documentStamp = document.getModificationStamp();

    file.setContent(null, "xxx", false);

    myDocumentManager.saveAllDocuments();

    assertEquals("old test", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals("old test", new String(file.contentsToByteArray(), StandardCharsets.UTF_8));
    assertEquals(documentStamp, document.getModificationStamp());
  }

  public void testReplaceDocumentTextWithTheSameText() throws Exception {
    final VirtualFile file = createFile();
    final DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);

    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, () -> {
      final String newText = "test text";
      document.replaceString(0, document.getTextLength(), newText);
      assertTrue(myDocumentManager.isDocumentUnsaved(document));
      myDocumentManager.saveDocument(document);

      getProject().getMessageBus().connect(getTestRootDisposable())
        .subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
          @Override
          public void beforeDocumentSaving(@NotNull Document documentToSave) {
            assertNotSame(document, documentToSave);
          }
        });

      final long modificationStamp = document.getModificationStamp();

      document.replaceString(0, document.getTextLength(), newText);
      if (myDocumentManager.isDocumentUnsaved(document)) {
        assertTrue(document.getModificationStamp() > modificationStamp);
      }
      else {
        assertEquals(modificationStamp, document.getModificationStamp());
      }
    });
  }

  public void testExternalReplaceWithTheSameText() throws Exception {
    final VirtualFile file = createFile();
    long modificationStamp = file.getModificationStamp();

    DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);
    FileUtil.writeToFile(new File(file.getPath()), "xxx");
    file.refresh(false, false);
    assertNotNull(file.toString(), document);

    assertNotSame(file.getModificationStamp(), modificationStamp);
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
  }


  public void testFileTypeModificationDocumentPreservation() throws Exception {
    File ioFile = IoTestUtil.createTestFile("test.html", "<html>some text</html>");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), file);

    Document original = myDocumentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    rename(file, "test.wtf");
    Document afterRename = myDocumentManager.getDocument(file);
    assertSame(afterRename + " != " + original, afterRename, original);
  }

  public void testFileTypeChangeDocumentDetach() throws Exception {
    File ioFile = IoTestUtil.createTestFile("test.html", "<html>some text</html>");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), file);

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    rename(file, "test.png");
    Document afterRename = documentManager.getDocument(file);
    assertNull(afterRename + " != null", afterRename);
  }

  public void testNoPSIModificationsDuringSave() {
    File ioFile = IoTestUtil.createTestFile("test.txt", "<html>some text</html>");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), virtualFile);

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(virtualFile);
    assertNotNull(virtualFile.getPath(), original);

    final PsiFile file = getPsiFile(original);
    assertNotNull(file);
    FileDocumentManagerListener saveListener = new FileDocumentManagerListener() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
          try {
            file.getFirstChild().delete();
            fail("Must not modify PSI inside save listener");
          }
          catch (IncorrectOperationException e) {
            assertEquals("Must not modify PSI inside save listener", e.getMessage());
          }
        });
      }
    };
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(FileDocumentManagerListener.TOPIC, saveListener);
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    assertNotNull(document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(1, "y"));

    FileDocumentManager.getInstance().saveAllDocuments();
  }

  public void testDocumentUnsavedInsideChangeListener() throws IOException {
    VirtualFile file = createFile("a.txt", "a");
    FileDocumentManager manager = FileDocumentManager.getInstance();
    Document document = manager.getDocument(file);
    assertFalse(manager.isDocumentUnsaved(document));

    AtomicInteger invoked = new AtomicInteger();
    AtomicBoolean expectUnsaved = new AtomicBoolean(true);
    DocumentListener listener = new DocumentListener() {
      @Override
      public void beforeDocumentChange(@NotNull DocumentEvent e) {
        assertFalse(manager.isDocumentUnsaved(document));
      }

      @Override
      public void documentChanged(@NotNull DocumentEvent event) {
        invoked.incrementAndGet();
        assertEquals(expectUnsaved.get(), manager.isDocumentUnsaved(document));
      }
    };
    document.addDocumentListener(listener, getTestRootDisposable());
    EditorFactory.getInstance().getEventMulticaster().addDocumentListener(listener, getTestRootDisposable());

    WriteCommandAction.runWriteCommandAction(myProject, () -> document.insertString(0, "b"));

    assertTrue(manager.isDocumentUnsaved(document));
    assertEquals(2, invoked.get());

    expectUnsaved.set(false);
    FileDocumentManager.getInstance().saveAllDocuments();
    FileUtil.writeToFile(VfsUtilCore.virtualToIoFile(file), "something");
    file.refresh(false, false);

    assertEquals("something", document.getText());
    assertFalse(manager.isDocumentUnsaved(document));
    assertEquals(4, invoked.get());
  }

  public void testGetFileFromConcurrentlyCreatedDocument() throws Exception {
    List<VirtualFile> physicalFiles = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      physicalFiles.add(createFile("a" + i + ".txt", "a" + i));
    }

    for (int iteration = 0; iteration < 10; iteration++) {
      GCWatcher.tracking(ContainerUtil.mapNotNull(physicalFiles, f -> FileDocumentManager.getInstance().getCachedDocument(f))).ensureCollected();

      checkDocumentFiles(physicalFiles);
      checkDocumentFiles(createNonPhysicalFiles());
    }
  }

  public void testDropAllUnsavedDocuments() throws Exception {
    VirtualFile file = createFile("test.txt", "unedited");
    Document document = myDocumentManager.getDocument(file);
    assertEquals("unedited", document.getText());

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.setText("edited"));
    assertEquals("edited", myDocumentManager.getDocument(file).getText());

    ApplicationManager.getApplication().runWriteAction(myDocumentManager::dropAllUnsavedDocuments);
    assertEquals("unedited", myDocumentManager.getDocument(file).getText());
  }

  public void testBeforeSaveAnyDocument_firedForUnchangedDocument() throws Exception {
    VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    ArrayList<Document> firedDocuments = new ArrayList<>();

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeAnyDocumentSaving(@NotNull Document document, boolean explicit) {
        firedDocuments.add(document);
      }
    });

    myDocumentManager.saveDocument(document);
    assertOrderedEquals(firedDocuments, document);
  }

  public void testBeforeSaveAnyDocument_firedBeforeBeforeDocumentSaving() throws Exception {
    VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    ArrayList<Document> firedDocuments = new ArrayList<>();
    ArrayList<Document> reallySavedDocuments = new ArrayList<>();

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeAnyDocumentSaving(@NotNull Document document, boolean explicit) {
        firedDocuments.add(document);
      }

      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        reallySavedDocuments.add(document);
        assertOrderedEquals(firedDocuments, document);
      }
    });

    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(0, "xxx"));
    myDocumentManager.saveDocument(document);
    assertOrderedEquals(firedDocuments, document);
    assertOrderedEquals(reallySavedDocuments, document);
  }

  private void checkDocumentFiles(List<? extends VirtualFile> files) throws Exception {
    List<Future<?>> futures = new ArrayList<>();
    for (VirtualFile file : files) {
      if (myDocumentManager.getCachedDocument(file) != null) {
        MemoryDumpHelper.captureMemoryDumpZipped("fileDocTest.hprof.zip");
        fail("Document not gc-ed: " + file);
      }
      for (int i = 0; i < 2; i++) {
        futures.add(ApplicationManager.getApplication().executeOnPooledThread(() -> ReadAction.run(() -> {
          Document document = myDocumentManager.getDocument(file);
          assertEquals(file, myDocumentManager.getFile(document));
        })));
      }
    }

    try {
      ConcurrencyUtil.getAll(20, TimeUnit.SECONDS, futures);
    }
    catch (TimeoutException e) {
      ThreadUtil.printThreadDump();
      throw e;
    }
  }

  @NotNull
  private static List<VirtualFile> createNonPhysicalFiles() {
    List<VirtualFile> allFiles = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      allFiles.add(new LightVirtualFile("b" + i + ".txt", "b" + i));
    }
    return allFiles;
  }

  public void testDocumentModificationStampMustChangeBeforeFileDeletion() {
    File ioFile = IoTestUtil.createTestFile("test.txt", "<html>some text</html>");
    VirtualFile myVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), myVirtualFile);

    DocumentEx document = (DocumentEx)myDocumentManager.getDocument(myVirtualFile);
    assertNotNull(myVirtualFile.getPath(), document);
    WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(1, "y"));
    long stampBefore = document.getModificationStamp();
    long sequenceBefore = document.getModificationSequence();

    delete(myVirtualFile);
    UIUtil.dispatchAllInvocationEvents();
    assertTrue(document.getModificationStamp() != stampBefore);
    assertTrue(document.getModificationSequence() > sequenceBefore);
  }
}
