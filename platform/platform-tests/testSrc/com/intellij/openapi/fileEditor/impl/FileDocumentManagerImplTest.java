// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.ide.actionsOnSave.impl.ActionsOnSaveManager;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManager.ConflictResolution;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.NonPhysicalFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
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
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
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

  public void testHardRegisteredNonPhysicalDocumentRemovedFromWeakCache() {
    VirtualFile file = new NonLightNonPhysicalVirtualFile("nonPhysical.txt", "test");
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(
      "The test file should have a document before hard registration",
      document
    );
    assertNotNull(
      "Non-LightVirtualFile documents start in myDocumentCache",
      myDocumentManager.getDocumentFromCacheInTests(file)
    );

    FileDocumentManagerBase.registerDocument(document, file);

    assertSame(
      "Cached document lookup should keep working via HARD_REF_TO_DOCUMENT_KEY",
      document,
      myDocumentManager.getCachedDocument(file)
    );
    assertNull(
      "Hard-bound documents must not leave a strong file key in myDocumentCache",
      myDocumentManager.getDocumentFromCacheInTests(file)
    );
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
    Future<?> future = ApplicationManager.getApplication().executeOnPooledThread(() ->
      ActionsOnSaveManager.Companion.getInstance(myProject).waitForTasks()
    );
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



  /** The Rider shape: a client owning its documents is never asked and never merged into. */
  public void testContentChanged_keepMemoryChangesIgnoresExternalChange() throws Exception {
    overrideConflictResolution(ConflictResolution.KEEP_MEMORY_CHANGES);

    Document document = editInMemoryThenOnDisk("rider.txt");

    assertEquals("first\nSECOND\nthird\n", document.getText());
  }


  /**
   * MERGE is declared but not implemented yet, so asking for it has to keep behaving like KEEP_MEMORY_CHANGES -- which is
   * what the clients migrating to it got from the boolean API before.
   */
  public void testContentChanged_mergeNotImplementedYetKeepsMemoryChanges() throws Exception {
    overrideConflictResolution(ConflictResolution.MERGE);

    Document document = editInMemoryThenOnDisk("merge-not-implemented.txt");

    assertEquals("first\nSECOND\nthird\n", document.getText());
  }

  /** Without an override the dialog is still the answer: merging only happens for a client that asked for it. */
  public void testContentChanged_defaultAsksWithoutAnyOverride() throws Exception {
    assertEquals("the premise of this test", ConflictResolution.ASK, myDocumentManager.getConflictResolution());
    myAskReloadFromDiskResult = Boolean.TRUE;

    Document document = editInMemoryThenOnDisk("default.txt");

    // the user was asked and picked the disk version, so the mergeable in-memory edit is dropped rather than combined
    assertEquals("first\nsecond\nTHIRD\n", document.getText());
  }






  private void overrideConflictResolution(@NotNull ConflictResolution resolution) {
    myDocumentManager.overrideConflictResolution(resolution, getTestRootDisposable());
  }

  /**
   * Edits the second line of a fresh file in memory and its third line on disk, so that the two sides are mergeable
   * and the merge result is distinguishable from either of them.
   */
  private @NotNull Document editInMemoryThenOnDisk(@NotNull String fileName) throws Exception {
    VirtualFile file = createFile(fileName, "first\nsecond\nthird\n");
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);

    WriteCommandAction.runWriteCommandAction(myProject, () -> document.setText("first\nSECOND\nthird\n"));
    setFileText(file, "first\nsecond\nTHIRD\n");
    PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
    return document;
  }



  public void testConflictResolutionOverrideIsBoundToDisposable() {
    assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());

    Disposable firstOverride = Disposer.newDisposable();
    Disposable secondOverride = Disposer.newDisposable();
    try {
      myDocumentManager.overrideConflictResolution(ConflictResolution.KEEP_MEMORY_CHANGES, firstOverride);
      assertEquals(ConflictResolution.KEEP_MEMORY_CHANGES, myDocumentManager.getConflictResolution());

      myDocumentManager.overrideConflictResolution(ConflictResolution.ASK, secondOverride);
      assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());

      Disposer.dispose(secondOverride);
      assertEquals(ConflictResolution.KEEP_MEMORY_CHANGES, myDocumentManager.getConflictResolution());
    }
    finally {
      Disposer.dispose(firstOverride);
      Disposer.dispose(secondOverride);
    }

    assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());
  }

  /** Two clients asking for the same resolution must stay distinguishable, or one disposal drops the other's entry. */
  public void testDisposingOneOfTwoEqualConflictResolutionOverridesKeepsTheOther() {
    Disposable firstOverride = Disposer.newDisposable();
    Disposable secondOverride = Disposer.newDisposable();
    try {
      myDocumentManager.overrideConflictResolution(ConflictResolution.KEEP_MEMORY_CHANGES, firstOverride);
      myDocumentManager.overrideConflictResolution(ConflictResolution.KEEP_MEMORY_CHANGES, secondOverride);

      Disposer.dispose(firstOverride);
      assertEquals(ConflictResolution.KEEP_MEMORY_CHANGES, myDocumentManager.getConflictResolution());

      Disposer.dispose(secondOverride);
      assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());
    }
    finally {
      Disposer.dispose(firstOverride);
      Disposer.dispose(secondOverride);
    }
  }

  /** MERGE yields to KEEP_MEMORY_CHANGES, but only while that override is actually alive. */
  public void testMergeOverrideAppliesOnceKeepMemoryChangesOverrideIsDisposed() {
    Disposable keepMemory = Disposer.newDisposable();
    Disposable merge = Disposer.newDisposable();
    try {
      myDocumentManager.overrideConflictResolution(ConflictResolution.KEEP_MEMORY_CHANGES, keepMemory);
      myDocumentManager.overrideConflictResolution(ConflictResolution.MERGE, merge);
      assertEquals(ConflictResolution.KEEP_MEMORY_CHANGES, myDocumentManager.getConflictResolution());

      Disposer.dispose(keepMemory);
      assertEquals(ConflictResolution.MERGE, myDocumentManager.getConflictResolution());
    }
    finally {
      Disposer.dispose(keepMemory);
      Disposer.dispose(merge);
    }
  }

  public void testDisposingOlderConflictResolutionOverrideDoesNotAffectNewerOverride() {
    assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());

    Disposable firstOverride = Disposer.newDisposable();
    Disposable secondOverride = Disposer.newDisposable();
    try {
      myDocumentManager.overrideConflictResolution(ConflictResolution.KEEP_MEMORY_CHANGES, firstOverride);
      myDocumentManager.overrideConflictResolution(ConflictResolution.ASK, secondOverride);

      Disposer.dispose(firstOverride);
      assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());
    }
    finally {
      Disposer.dispose(firstOverride);
      Disposer.dispose(secondOverride);
    }

    assertEquals(ConflictResolution.ASK, myDocumentManager.getConflictResolution());
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


  public void testFileTypeModificationDocumentPreservation() {
    File ioFile = IoTestUtil.createTestFile("test.html", "<html>some text</html>");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), file);

    Document original = myDocumentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    rename(file, "test.wtf");
    Document afterRename = myDocumentManager.getDocument(file);
    assertSame(afterRename + " != " + original, afterRename, original);
  }

  public void testFileTypeChangeDocumentDetach() {
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
    List<Document> firedDocuments = new ArrayList<>();
    List<Document> reallySavedDocuments = new ArrayList<>();

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
  public void testAfterDocumentSavedListener() throws Exception {
    VirtualFile file = createFile();
    Document myDoc = myDocumentManager.getDocument(file);
    List<String> log = Collections.synchronizedList(new ArrayList<>());

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        if (document == myDoc) {
          assertTrue(FileDocumentManager.getInstance().isDocumentUnsaved(document));
          log.add("BS");
        }
      }

      @Override
      public void afterDocumentSaved(@NotNull Document document) {
        if (document == myDoc) {
          assertFalse(FileDocumentManager.getInstance().isDocumentUnsaved(document));
          log.add("AS");
        }
      }
    });

    WriteCommandAction.runWriteCommandAction(getProject(), () -> myDoc.insertString(0, "xxx"));
    myDocumentManager.saveDocument(myDoc);
    assertOrderedEquals(log, "BS", "AS");
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

  private static final VirtualFileSystem NON_LIGHT_NON_PHYSICAL_FILE_SYSTEM = new TestNonPhysicalFileSystem();

  private static final class NonLightNonPhysicalVirtualFile extends MockVirtualFile {
    private NonLightNonPhysicalVirtualFile(@NotNull String name, @NotNull String text) {
      super(name, text);
    }

    @Override
    public @NotNull VirtualFileSystem getFileSystem() {
      return NON_LIGHT_NON_PHYSICAL_FILE_SYSTEM;
    }
  }

  private static final class TestNonPhysicalFileSystem extends DeprecatedVirtualFileSystem implements NonPhysicalFileSystem {
    @Override
    public @NotNull String getProtocol() {
      return "non-light-non-physical";
    }

    @Override
    public @Nullable VirtualFile findFileByPath(@NotNull String path) {
      return null;
    }

    @Override
    public void refresh(boolean asynchronous) { }

    @Override
    public @Nullable VirtualFile refreshAndFindFileByPath(@NotNull String path) {
      return null;
    }
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

  public void testLightFileDocumentCaching() {
    var lightFile = new LightVirtualFile("testFile.txt", "test");
    assertNull("File is not expected to have a document", myDocumentManager.getCachedDocument(lightFile));
    var lightFileDocument = myDocumentManager.getDocument(lightFile);
    assertNotNull("Document should be created for the light file", lightFileDocument);
    var lightFileDocumentRef = new WeakReference<>(lightFileDocument);
    assertTrue("Document is expected to be in the cached docs", isDocumentCached(lightFileDocument));
    //noinspection UnusedAssignment
    lightFile = null;
    GCUtil.tryGcSoftlyReachableObjects();
    assertTrue("Document is expected to be in the cached docs", isDocumentCached(lightFileDocument));
    //noinspection UnusedAssignment
    lightFileDocument = null;
    GCUtil.tryGcSoftlyReachableObjects();
    assertNull("Document is expected to be GCed at this point", lightFileDocumentRef.get());
  }

  private boolean isDocumentCached(@NotNull Document document) {
    var result = new boolean[1];
    myDocumentManager.forEachCachedDocument(doc -> {
      if (doc == document) {
        result[0] = true;
      }
    });
    return result[0];
  }
}
