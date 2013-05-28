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

import com.intellij.codeStyle.CodeStyleFacade;
import com.intellij.codeStyle.DefaultCodeStyleFacade;
import com.intellij.ide.DataManager;
import com.intellij.ide.impl.DataManagerImpl;
import com.intellij.mock.MockCommandProcessor;
import com.intellij.mock.MockEditorFactory;
import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.fileEditor.impl.FileDocumentManagerImpl;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.FileTypeManagerImpl;
import com.intellij.openapi.fileTypes.impl.InternalFileTypeFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.impl.DefaultProjectLocator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.MockSchemesManagerFactory;
import com.intellij.testFramework.PlatformLiteFixture;
import com.intellij.util.LocalTimeCounter;
import org.easymock.classextension.EasyMock;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

public class FileDocumentManagerImplTest extends PlatformLiteFixture {
  private MyMockFileDocumentManager myDocumentManager;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initApplication();
    registerExtension(FileTypeFactory.FILE_TYPE_FACTORY_EP, new InternalFileTypeFactory());
    registerExtensionPoint(FileDocumentSynchronizationVetoer.EP_NAME, FileDocumentSynchronizationVetoer.class);
    getApplication().registerService(CommandProcessor.class, new MyMockCommandProcessor());
    getApplication().registerService(CodeStyleFacade.class, new DefaultCodeStyleFacade());
    getApplication().registerService(ProjectLocator.class, new DefaultProjectLocator());

    MockEditorFactory editorFactory = new MockEditorFactory();
    getApplication().registerService(EditorFactory.class, editorFactory);
    final LanguageFileType[] fileType = {null};
    getApplication().addComponent(FileTypeManager.class, new FileTypeManagerImpl(null, new MockSchemesManagerFactory()) {
      @NotNull
      @Override
      public FileType getFileTypeByFileName(@NotNull String fileName) {
        return fileType[0];
      }

      @NotNull
      @Override
      public FileType getFileTypeByFile(@NotNull VirtualFile file) {
        return fileType[0];
      }

      @NotNull
      @Override
      public FileType getFileTypeByExtension(@NotNull String extension) {
        return fileType[0];
      }
    });

    fileType[0] = StdFileTypes.JAVA;

    getApplication().getComponent(FileTypeManager.class);

    final VirtualFileManager virtualFileManager = EasyMock.createMock(VirtualFileManager.class);
    final ProjectManager projectManager = EasyMock.createMock(ProjectManager.class);
    myDocumentManager = new MyMockFileDocumentManager(virtualFileManager, projectManager);
    getApplication().registerService(FileDocumentManager.class, myDocumentManager);
    getApplication().registerService(DataManager.class, new DataManagerImpl());
  }

  public void testGetCachedDocument_Cached() throws Exception {
    final Document cachedDocument = myDocumentManager.getCachedDocument(new MockVirtualFile("test.txt"));
    assertNull(cachedDocument);
  }

  public void testGetCachedDocument_NotCached() throws Exception {
    final VirtualFile file = newTextFile();
    final Document document = myDocumentManager.getDocument(file);

    assertSame(myDocumentManager.getCachedDocument(file), document);
  }

  public void testGetDocument_CreateNew() throws Exception {
    final VirtualFile file = newTextFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(document);
    assertEquals("test", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(file.isWritable(), document.isWritable());
  }

  public void testGetDocument_CreateNew_ReadOnly() throws Exception {
    final MockVirtualFile file = newTextFile();
    file.setWritable(false);
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(document);
    assertEquals("test", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
    assertEquals(file.isWritable(), document.isWritable());
  }

  public void testGetDocument_ReturnCachedValueTwice() throws Exception {
    final VirtualFile file = newTextFile();
    final Document document = myDocumentManager.getDocument(file);
    assertNotNull(document);
    assertEquals("test", document.getText());

    final Document document2 = myDocumentManager.getDocument(file);
    assertSame(document2, document);
  }

  public void testGetDocument_CreatesNewAfterGCed() throws Exception {
    final VirtualFile file = newTextFile();
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
    final VirtualFile file = newTextFile();
    myDocumentManager.getDocument(file);

    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_ModifiedDocument() throws Exception {
    final VirtualFile file = newTextFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");

    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(1, unsavedDocuments.length);
    assertSame(document, unsavedDocuments[0]);
    assertTrue(Arrays.equals("test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testGetUnsavedDocuments_afterSaveAllDocuments() throws Exception {
    final VirtualFile file = newTextFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");

    myDocumentManager.saveAllDocuments();
    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_afterSaveDocuments() throws Exception {
    final VirtualFile file = newTextFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");

    myDocumentManager.saveDocument(document);
    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(0, unsavedDocuments.length);
  }

  public void testGetUnsavedDocuments_afterSaveDocumentWithProblems() throws Exception {
    final VirtualFile file = new MockVirtualFile("test.txt", "test") {
      @Override
      @NotNull
      public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        throw new IOException("");
      }
    };

    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");

    myDocumentManager.saveDocument(document);

    assertNotNull(myDocumentManager.myExceptionOnSave);

    final Document[] unsavedDocuments = myDocumentManager.getUnsavedDocuments();
    assertEquals(1, unsavedDocuments.length);
    assertSame(document, unsavedDocuments[0]);
    assertTrue(Arrays.equals("test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testUnsavedDocument_DoNotGC() throws Exception {
    final VirtualFile file = newTextFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");
    int idCode = System.identityHashCode(document);
    //noinspection UnusedAssignment
    document = null;

    System.gc();
    System.gc();

    document = myDocumentManager.getDocument(file);
    assertEquals(idCode, System.identityHashCode(document));
  }

  public void testUnsavedDocument_GcAfterSave() throws Exception {
    final VirtualFile file = newTextFile();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");
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
    final VirtualFile file = newTextFile();
    final long stamp = file.getModificationStamp();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    myDocumentManager.saveDocument(document);
    assertEquals(stamp, file.getModificationStamp());
  }

  public void testSaveDocument_DocumentWasChanged() throws Exception {
    final VirtualFile file = newTextFile();
    final long stamp = file.getModificationStamp();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx ");

    myDocumentManager.saveDocument(document);
    assertTrue(stamp != file.getModificationStamp());
    assertEquals(document.getModificationStamp(), file.getModificationStamp());
    assertTrue(Arrays.equals("xxx test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testSaveAllDocuments_DocumentWasChanged() throws Exception {
    final VirtualFile file = newTextFile();
    final long stamp = file.getModificationStamp();
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx ");

    myDocumentManager.saveAllDocuments();
    assertTrue(stamp != file.getModificationStamp());
    assertTrue(Arrays.equals("xxx test".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testGetFile() throws Exception {
    final VirtualFile file = newTextFile();
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
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx ");
    myDocumentManager.saveAllDocuments();
    assertTrue(Arrays.equals("xxx test\rtest".getBytes("UTF-8"), file.contentsToByteArray()));
  }

  public void testContentChanged_noDocument() throws Exception {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    file.setListener(myDocumentManager);
    file.setContent(null, "xxx", true);
    assertNull(myDocumentManager.getCachedDocument(file));
  }

  public void testContentChanged_documentPresent() throws Exception {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    file.setListener(myDocumentManager);
    Document document = myDocumentManager.getDocument(file);
    file.setContent(null, "xxx", true);
    assertNotNull(file.toString(), document);
    assertEquals("xxx", document.getText());
    assertEquals(file.getModificationStamp(), document.getModificationStamp());
  }

  public void testContentChanged_ignoreEventsFromSelf() throws Exception {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    file.setListener(myDocumentManager);
    Document document = myDocumentManager.getDocument(file);
    file.setContent(myDocumentManager, "xxx", true);
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
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "xxx");
    final long stamp = document.getModificationStamp();

    myDocumentManager.saveAllDocuments();
    assertEquals(stamp, document.getModificationStamp());
  }

  public void testContentChanged_reloadChangedDocument() throws Exception {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test\rtest");
    file.setListener(myDocumentManager);
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "zzz");

    myDocumentManager.myReloadFromDisk = Boolean.TRUE;
    try {
      file.setContent(null, "xxx", true);

      assertEquals("xxx", document.getText());
      assertEquals(file.getModificationStamp(), document.getModificationStamp());
      assertEquals(0, myDocumentManager.getUnsavedDocuments().length);
    }
    finally {
      myDocumentManager.myReloadFromDisk = null;
    }
  }

  public void testContentChanged_DoNotReloadChangedDocument() throws Exception {
    final MockVirtualFile file = newTextFile();
    file.setListener(myDocumentManager);
    Document document = myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "old ");

    myDocumentManager.myReloadFromDisk = Boolean.FALSE;
    try {
      long oldDocumentStamp = document.getModificationStamp();

      file.setContent(null, "xxx", true);

      assertEquals("old test", document.getText());
      assertEquals(oldDocumentStamp, document.getModificationStamp());
    }
    finally {
      myDocumentManager.myReloadFromDisk = null;
    }
  }

  public void testSaveDocument_DoNotSaveIfModStampEqualsToFile() throws Exception {
    final VirtualFile file = new MockVirtualFile("test.txt", "test") {
      @NotNull
      @Override
      public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        fail();
        throw new IOException();
      }
    };
    DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);
    assertNotNull(file.toString(), document);
    document.insertString(0, "zzz");
    document.setModificationStamp(file.getModificationStamp());

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

    myDocumentManager.myReloadFromDisk = Boolean.TRUE;
    try {
      myDocumentManager.saveAllDocuments();
      long fileStamp = file.getModificationStamp();

      assertEquals("xxx", document.getText());
      assertEquals(file.getModificationStamp(), document.getModificationStamp());
      assertEquals(file.getModificationStamp(), fileStamp);
      assertEquals(0, myDocumentManager.getUnsavedDocuments().length);
    }
    finally {
      myDocumentManager.myReloadFromDisk = null;
    }
  }

  public void testContentChanged_doNotReloadChangedDocumentOnSave() throws Exception {
    final MockVirtualFile file = new MockVirtualFile("test.txt", "test") {
      @Override
      public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        long oldStamp = getModificationStamp();
        setModificationStamp(LocalTimeCounter.currentTime());
        myDocumentManager.contentsChanged(new VirtualFileEvent(null, this, null, oldStamp, getModificationStamp()));
      }
    };

    myDocumentManager.myReloadFromDisk = Boolean.FALSE;
    try {
      Document document = myDocumentManager.getDocument(file);
      assertNotNull(file.toString(), document);
      document.insertString(0, "old ");
      long documentStamp = document.getModificationStamp();

      file.setContent(null, "xxx", false);

      myDocumentManager.saveAllDocuments();

      assertEquals("old test", document.getText());
      assertEquals(file.getModificationStamp(), document.getModificationStamp());
      assertTrue(Arrays.equals("old test".getBytes("UTF-8"), file.contentsToByteArray()));
      assertEquals(documentStamp, document.getModificationStamp());
    }
    finally {
      myDocumentManager.myReloadFromDisk = null;
    }
  }

  public void testReplaceDocumentTestWithTheSameTest() throws Exception {
    final boolean[] canCallSave = {true};
    final VirtualFile file = new MockVirtualFile("test.txt", "test") {
      @NotNull
      @Override
      public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        if (!canCallSave[0]) fail();
        return super.getOutputStream(requestor, newModificationStamp, newTimeStamp);
      }
    };
    DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);

    String newText = "test text";
    assertNotNull(file.toString(), document);
    document.replaceString(0, document.getTextLength(), newText);
    assertTrue(myDocumentManager.isDocumentUnsaved(document));
    myDocumentManager.saveDocument(document);

    canCallSave[0] = false;

    final long modificationStamp = document.getModificationStamp();

    document.replaceString(0, document.getTextLength(), newText);

    if (myDocumentManager.isDocumentUnsaved(document)) {
      assertTrue(document.getModificationStamp() > modificationStamp);
    }
    else {
      assertEquals(modificationStamp, document.getModificationStamp());
    }
  }

  public void testExternalReplaceWithTheSameText() throws Exception {
    final long[] modificationStamp = new long[1];
    modificationStamp[0] = 1;

    final VirtualFile file = new MockVirtualFile("test.txt", "test") {
      @NotNull
      @Override
      public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp) throws IOException {
        fail();
        throw new IOException();
      }

      @Override
      public long getModificationStamp() {
        return modificationStamp[0];
      }

      @Override
      public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
        myDocumentManager.contentsChanged(new VirtualFileEvent(null, this, null, 1, 2));
      }
    };

    DocumentEx document = (DocumentEx)myDocumentManager.getDocument(file);
    modificationStamp[0] = 2;
    file.refresh(false, false);
    assertNotNull(file.toString(), document);
    assertEquals(2, document.getModificationStamp());
  }

  private static MockVirtualFile newTextFile() {
    return new MockVirtualFile("test.txt", "test");
  }

  private static class MyMockFileDocumentManager extends FileDocumentManagerImpl {
    private static final FileDocumentManagerListener[] LISTENERS = new FileDocumentManagerListener[0];

    private Collection<IOException> myExceptionOnSave = null;
    private Boolean myReloadFromDisk = null;

    public MyMockFileDocumentManager(VirtualFileManager virtualFileManager, ProjectManager projectManager) {
      super(virtualFileManager, projectManager);
    }

    @Override
    protected void handleErrorsOnSave(@NotNull Map<Document, IOException> failures) {
      myExceptionOnSave = failures.values();
    }

    @Override
    protected boolean askReloadFromDisk(VirtualFile file, Document document) {
      if (myReloadFromDisk == null) {
        fail();
        return false;
      }
      else {
        return myReloadFromDisk.booleanValue();
      }
    }

    @NotNull
    @Override
    protected FileDocumentManagerListener[] getListeners() {
      return LISTENERS;
    }
  }

  private static class MyMockCommandProcessor extends MockCommandProcessor {
    @Override
    public void executeCommand(Project project, @NotNull Runnable runnable, String name, Object groupId) {
      runnable.run();
    }

    @Override
    public void executeCommand(Project project,
                               @NotNull Runnable runnable,
                               String name,
                               Object groupId,
                               @NotNull UndoConfirmationPolicy confirmationPolicy,
                               Document document) {
      runnable.run();
    }

    @Override
    public void executeCommand(Project project,
                               @NotNull Runnable runnable,
                               String name,
                               Object groupId,
                               @NotNull UndoConfirmationPolicy confirmationPolicy) {
      runnable.run();
    }

    @Override
    public void executeCommand(@NotNull Runnable runnable, String name, Object groupId) {
      runnable.run();
    }
  }
}
