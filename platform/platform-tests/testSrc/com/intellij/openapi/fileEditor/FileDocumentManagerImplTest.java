/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.AppTopics;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformLangTestCase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.LocalTimeCounter;
import com.intellij.util.PairProcessor;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class FileDocumentManagerImplTest extends PlatformLangTestCase {
  private FileDocumentManagerImpl myDocumentManager;

  public Boolean myReloadFromDisk;
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myReloadFromDisk = Boolean.TRUE;
    FileDocumentManagerImpl impl = (FileDocumentManagerImpl)FileDocumentManager.getInstance();
    impl.setAskReloadFromDisk(getTestRootDisposable(), new PairProcessor<VirtualFile, Document>() {
      @Override
      public boolean process(VirtualFile file, Document document) {
        if (myReloadFromDisk == null) {
          fail();
          return false;
        }
        return myReloadFromDisk.booleanValue();
      }
    });
    myDocumentManager = impl;
  }

  public void testGetCachedDocument_Cached() throws Exception {
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
    file.setWritable(false);
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

    System.gc();
    System.gc();

    document = myDocumentManager.getDocument(file);
    assertTrue(idCode != System.identityHashCode(document));
  }

  public void testGetUnsavedDocuments_NoDocuments() throws Exception {
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
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx");
      }
    });


    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(1, unsavedDocuments.length);
    assertSame(document, unsavedDocuments[0]);
    assertTrue(Arrays.equals("test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testGetUnsavedDocuments_afterSaveAllDocuments() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx");
      }
    });


    myDocumentManager.saveAllDocuments();
    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_afterSaveDocuments() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx");
      }
    });


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
      WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
        @Override
        public void run() {
          document.insertString(0, "xxx");
        }
      });


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
      assertTrue(Arrays.equals("test".getBytes("UTF-8"), file.contentsToByteArray()));
    }
    finally {
      myDocumentManager.dropAllUnsavedDocuments();
    }
  }

  public void testUnsavedDocument_DoNotGC() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    int idCode = System.identityHashCode(document);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        myDocumentManager.getDocument(file).insertString(0, "xxx");
      }
    });

    //noinspection UnusedAssignment
    document = null;

    System.gc();
    System.gc();

    document = myDocumentManager.getDocument(file);
    assertEquals(idCode, System.identityHashCode(document));
  }

  public void testUnsavedDocument_GcAfterSave() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        myDocumentManager.getDocument(file).insertString(0, "xxx");
      }
    });

    int idCode = System.identityHashCode(document);
    //noinspection UnusedAssignment
    document = null;

    myDocumentManager.saveAllDocuments();

    System.gc();
    System.gc();

    document = myDocumentManager.getDocument(file);
    assertTrue(idCode != System.identityHashCode(document));
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
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx ");
      }
    });


    myDocumentManager.saveDocument(document);
    assertTrue(stamp != file.getModificationStamp());
    assertEquals(document.getModificationStamp(), file.getModificationStamp());
    assertTrue(Arrays.equals("xxx test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testSaveAllDocuments_DocumentWasChanged() throws Exception {
    final VirtualFile file = createFile();
    final long stamp = file.getModificationStamp();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx ");
      }
    });


    myDocumentManager.saveAllDocuments();
    assertTrue(stamp != file.getModificationStamp());
    assertTrue(Arrays.equals("xxx test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testGetFile() throws Exception {
    final VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    assertSame(file, myDocumentManager.getFile(document));
  }

  public void testConvertSeparators() throws Exception {
    final VirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    assertEquals("test\ntest", document.getText());
  }

  public void testRememberSeparators() throws Exception {
    final VirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx ");
      }
    });

    myDocumentManager.saveAllDocuments();
    assertTrue(Arrays.equals("xxx test\rtest".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testContentChanged_noDocument() throws Exception {
    final VirtualFile file = createFile();
    setContent(file, "xxx");
    assertNull(myDocumentManager.getCachedDocument(file));
  }

  VirtualFile createFile(String name, String content) throws IOException {
    File file = createTempFile(name, content);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file);
    assertNotNull(virtualFile);
    return virtualFile;
  }
  VirtualFile createFile() throws IOException {
    return createFile("test.txt", "test");
  }
  void setContent(VirtualFile file, String content) throws IOException {
    file.setBinaryContent(content.getBytes(CharsetToolkit.UTF8_CHARSET));
  }

  public void testContentChanged_documentPresent() throws Exception {
    VirtualFile file = createFile();
    Document document = myDocumentManager.getDocument(file);
    setContent(file, "xxx");
    assertNotNull(file.toString(), document);
    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
  }

  public void testContentChanged_ignoreEventsFromSelf() throws Exception {
    final VirtualFile file = createFile("test.txt", "test\rtest");
    Document document = myDocumentManager.getDocument(file);
    file.setBinaryContent("xxx".getBytes(CharsetToolkit.UTF8_CHARSET), -1,-1,myDocumentManager);
    assertNotNull(file.toString(), document);
    assertEquals("test\ntest", document.getText());
  }

  public void testContentChanged_ignoreEventsFromSelfOnSave() throws Exception {
    final VirtualFile file = new MockVirtualFile("test.txt", "test\rtest") {
      @NotNull
      @Override
      public OutputStream getOutputStream(final Object requestor, final long newModificationStamp, long newTimeStamp) throws IOException {
        final VirtualFile self = this;
        return new ByteArrayOutputStream() {
          @Override
          public void close() throws IOException {
            super.close();
            long oldStamp = getModificationStamp();
            setModificationStamp(newModificationStamp);
            setText(toString());
            myDocumentManager.contentsChanged(new VirtualFileEvent(requestor, self, null, oldStamp, getModificationStamp()));
          }
        };
      }
    };
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "xxx");
      }
    });

    final long stamp = document.getModificationStamp();

    myDocumentManager.saveAllDocuments();
    assertEquals(stamp, document.getModificationStamp());
  }

  public void testContentChanged_reloadChangedDocument() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "zzz");
      }
    });


    myReloadFromDisk = Boolean.TRUE;
    setContent(file, "xxx");

    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(0, myDocumentManager.getUnsavedDocuments().length);
  }

  public void testContentChanged_DoNotReloadChangedDocument() throws Exception {
    final VirtualFile file = createFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "old ");
      }
    });

    myReloadFromDisk = Boolean.FALSE;
    long oldDocumentStamp = document.getModificationStamp();

    file.setBinaryContent("xxx".getBytes(CharsetToolkit.UTF8_CHARSET));

    assertEquals("old test", document.getText());
    assertEquals(oldDocumentStamp, document.getModificationStamp());
  }

  public void testSaveDocument_DoNotSaveIfModStampEqualsToFile() throws Exception {
    final VirtualFile file = createFile();
    final DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "zzz");
        document.setModificationStamp(file.getModificationStamp());
      }
    });

    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
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
  public void _testContentChanged_reloadChangedDocumentOnSave() throws Exception {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test\rtest") {
      @Override
      public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        long oldStamp = getModificationStamp();
        setModificationStamp(LocalTimeCounter.currentTime());
        myDocumentManager.contentsChanged(new VirtualFileEvent(null, this, null, oldStamp, getModificationStamp()));
      }
    };
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "zzz");
    file.setContent(null, "xxx", false);

    myReloadFromDisk = Boolean.TRUE;
    myDocumentManager.saveAllDocuments();
    long fileStamp = file.getModificationStamp();

    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(file.getModificationStamp(), fileStamp);
    assertEquals(0, myDocumentManager.getUnsavedDocuments().length);
  }

  public void testContentChanged_doNotReloadChangedDocumentOnSave() throws Exception {
    final MockVirtualFile file =
    new MockVirtualFile("test.txt", "test") {
      @Override
      public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        long oldStamp = getModificationStamp();
        setModificationStamp(LocalTimeCounter.currentTime());
        myDocumentManager.contentsChanged(new VirtualFileEvent(null, this, null, oldStamp, getModificationStamp()));
      }
    };

    myReloadFromDisk = Boolean.FALSE;
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "old ");
      }
    });

    long documentStamp = document.getModificationStamp();

    file.setContent(null, "xxx", false);

    myDocumentManager.saveAllDocuments();

    assertEquals("old test", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertTrue(Arrays.equals("old test".getBytes("UTF-8"), file.contentsToByteArray()));
    assertEquals(documentStamp, document.getModificationStamp());
  }

  public void testReplaceDocumentTextWithTheSameText() throws Exception {
    final VirtualFile file = createFile();
    final DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);

    final String newText = "test text";
    assertNotNull(file.toString(), document);
    WriteCommandAction.runWriteCommandAction(myProject, new Runnable() {
      @Override
      public void run() {
        document.replaceString(0, document.getTextLength(), newText);
        assertTrue(myDocumentManager.isDocumentUnsaved(document));
        myDocumentManager.saveDocument(document);

        getProject().getMessageBus().connect(getTestRootDisposable())
          .subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
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

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    renameFile(file, "test.wtf");
    Document afterRename = documentManager.getDocument(file);
    assertTrue(afterRename + " != " + original, afterRename == original);
  }

  public void testFileTypeChangeDocumentDetach() throws Exception {
    File ioFile = IoTestUtil.createTestFile("test.html", "<html>some text</html>");
    VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), file);

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(file);
    assertNotNull(file.getPath(), original);

    renameFile(file, "test.png");
    Document afterRename = documentManager.getDocument(file);
    assertNull(afterRename + " != null", afterRename);
  }

  private static void renameFile(VirtualFile file, String newName) throws IOException {
    AccessToken token = ApplicationManager.getApplication().acquireWriteActionLock(null);
    try {
      file.rename(null, newName);
    }
    finally {
      token.finish();
    }
  }

  public void testNoPSIModificationsDuringSave() throws IOException {
    File ioFile = IoTestUtil.createTestFile("test.txt", "<html>some text</html>");
    VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile);
    assertNotNull(ioFile.getPath(), virtualFile);

    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document original = documentManager.getDocument(virtualFile);
    assertNotNull(virtualFile.getPath(), original);

    final PsiFile file = getPsiFile(original);
    FileDocumentManagerListener saveListener = new FileDocumentManagerAdapter() {
      @Override
      public void beforeDocumentSaving(@NotNull Document document) {
        WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
          @Override
          public void run() {
            try {
              file.getFirstChild().delete();
              fail("Must not modify PSI inside save listener");
            }
            catch (IncorrectOperationException e) {
              assertEquals("Must not modify PSI inside save listener", e.getMessage());
            }
          }
        });
      }
    };
    getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(AppTopics.FILE_DOCUMENT_SYNC, saveListener);
    final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
    WriteCommandAction.runWriteCommandAction(getProject(), new Runnable() {
      @Override
      public void run() {
        document.insertString(1,"y");
      }
    });

    FileDocumentManager.getInstance().saveAllDocuments();
  }
}
