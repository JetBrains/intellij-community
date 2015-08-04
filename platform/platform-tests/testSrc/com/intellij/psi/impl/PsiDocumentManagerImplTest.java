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
package com.intellij.psi.impl;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.ide.impl.ProjectUtil;
import com.intellij.mock.MockDocument;
import com.intellij.mock.MockPsiFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.event.DocumentEventImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.ui.UIUtil;

import java.io.File;
import java.io.IOException;
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

    final Document document = getPsiDocumentManager().getDocument(file);
    assertNotNull(document);
    assertSame(document, FileDocumentManager.getInstance().getDocument(vFile));
  }

  @Override
  protected boolean isRunInWriteAction() {
    return false;
  }

  private static LightVirtualFile createFile() {
    return new LightVirtualFile("foo.txt");
  }

  public void testDocumentGced() throws Exception {
    VirtualFile vFile = getVirtualFile(createTempFile("txt", "abc"));
    PsiDocumentManagerImpl documentManager = getPsiDocumentManager();
    long id = System.identityHashCode(documentManager.getDocument(getPsiManager().findFile(vFile)));

    documentManager.commitAllDocuments();
    UIUtil.dispatchAllInvocationEvents();
    UIUtil.dispatchAllInvocationEvents();
    assertEmpty(documentManager.getUncommittedDocuments());

    LeakHunter.checkLeak(documentManager, DocumentImpl.class);
    LeakHunter.checkLeak(documentManager, PsiFileImpl.class, new Processor<PsiFileImpl>() {
      @Override
      public boolean process(PsiFileImpl psiFile) {
        return psiFile.getViewProvider().getVirtualFile().getFileSystem() instanceof LocalFileSystem;
      }
    });
    //Class.forName("com.intellij.util.ProfilingUtil").getDeclaredMethod("forceCaptureMemorySnapshot").invoke(null);

    for (int i = 0; i < 1000; i++) {
      PlatformTestUtil.tryGcSoftlyReachableObjects();
      UIUtil.dispatchAllInvocationEvents();
      if (documentManager.getCachedDocument(getPsiManager().findFile(vFile)) == null) break;
      System.gc();
    }
    assertNull(documentManager.getCachedDocument(getPsiManager().findFile(vFile)));

    Document newDoc = documentManager.getDocument(getPsiManager().findFile(vFile));
    assertTrue(id != System.identityHashCode(newDoc));
  }

  public void testGetUncommittedDocuments_noDocuments() throws Exception {
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentChanged_DontProcessEvents() throws Exception {
    final PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        getPsiDocumentManager().getSynchronizer().performAtomically(file, new Runnable() {
          @Override
          public void run() {
            changeDocument(document, PsiDocumentManagerImplTest.this.getPsiDocumentManager());
          }
        });
      }
    });


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testGetUncommittedDocuments_documentNotRegistered() throws Exception {
    final Document document = new MockDocument();

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        changeDocument(document, PsiDocumentManagerImplTest.this.getPsiDocumentManager());
      }
    });


    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testCommitDocument_RemovesFromUncommittedList() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        changeDocument(document, PsiDocumentManagerImplTest.this.getPsiDocumentManager());
      }
    });


    getPsiDocumentManager().commitDocument(document);
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  private static void changeDocument(Document document, PsiDocumentManagerImpl manager) {
    DocumentEventImpl event = new DocumentEventImpl(document, 0, "", "", document.getModificationStamp(), false);
    manager.beforeDocumentChange(event);
    manager.documentChanged(event);
  }

  public void testCommitAllDocument_RemovesFromUncommittedList() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        changeDocument(document, getPsiDocumentManager());
      }
    });


    getPsiDocumentManager().commitAllDocuments();
    assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
  }

  public void testDocumentFromAlienProjectDoesNotEndUpInMyUncommittedList() throws Exception {
    PsiFile file = getPsiManager().findFile(createFile());

    final Document document = getPsiDocumentManager().getDocument(file);

    File temp = createTempDirectory();
    final Project alienProject = createProject(new File(temp, "alien.ipr"), DebugUtil.currentStackTrace());
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
      //alienDocument.putUserData(CACHED_VIEW_PROVIDER, new MockFileViewProvider(alienFile));
      assertEquals(0, alienDocManager.getUncommittedDocuments().length);
      assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);

      WriteCommandAction.runWriteCommandAction(null, new Runnable() {
        @Override
        public void run() {
          changeDocument(alienDocument, PsiDocumentManagerImplTest.this.getPsiDocumentManager());
          assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(0, alienDocManager.getUncommittedDocuments().length);

          changeDocument(alienDocument, alienDocManager);
          assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(1, alienDocManager.getUncommittedDocuments().length);

          changeDocument(document, PsiDocumentManagerImplTest.this.getPsiDocumentManager());
          assertEquals(1, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(1, alienDocManager.getUncommittedDocuments().length);

          changeDocument(document, alienDocManager);
          assertEquals(1, getPsiDocumentManager().getUncommittedDocuments().length);
          assertEquals(1, alienDocManager.getUncommittedDocuments().length);
        }
      });
    }
    finally {
      ProjectUtil.closeAndDispose(alienProject);
    }
  }

  public void testCommitInBackground() {
    PsiFile file = getPsiManager().findFile(createFile());
    assertNotNull(file);
    assertTrue(file.isPhysical());
    final Document document = getPsiDocumentManager().getDocument(file);
    assertNotNull(document);

    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(new Runnable() {
      @Override
      public void run() {
        assertTrue(getPsiDocumentManager().isCommitted(document));
        semaphore.up();
      }
    });
    waitAndPump(semaphore, TIMEOUT);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "class X {}");
      }
    });

    semaphore.down();
    getPsiDocumentManager().performWhenAllCommitted(new Runnable() {
      @Override
      public void run() {
        assertTrue(getPsiDocumentManager().isCommitted(document));
        semaphore.up();
      }
    });
    waitAndPump(semaphore, TIMEOUT);
    assertTrue(getPsiDocumentManager().isCommitted(document));

    final AtomicInteger count = new AtomicInteger();
    final Runnable action = new Runnable() {
      @Override
      public void run() {
        count.incrementAndGet();
      }
    };

    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "/**/");
        boolean executed = getPsiDocumentManager().cancelAndRunWhenAllCommitted("xxx", action);
        assertFalse(executed);
        executed = getPsiDocumentManager().cancelAndRunWhenAllCommitted("xxx", action);
        assertFalse(executed);
        assertEquals(0, count.get());
      }
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertEquals(1, count.get());

    count.set(0);
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        document.insertString(0, "/**/");
        boolean executed = getPsiDocumentManager().performWhenAllCommitted(action);
        assertFalse(executed);
        executed = getPsiDocumentManager().performWhenAllCommitted(action);
        assertFalse(executed);
        assertEquals(0, count.get());
      }
    });

    while (!getPsiDocumentManager().isCommitted(document)) {
      UIUtil.dispatchAllInvocationEvents();
    }
    assertTrue(getPsiDocumentManager().isCommitted(document));
    assertEquals(2, count.get());
  }

  private static void waitAndPump(Semaphore semaphore, int timeout) {
    final long limit = System.currentTimeMillis() + timeout;
    while (System.currentTimeMillis() < limit) {
      if (semaphore.waitFor(10)) return;
      UIUtil.dispatchAllInvocationEvents();
    }
    fail("Timeout");
  }

  public void testDocumentFromAlienProjectGetsCommittedInBackground() throws Exception {
    LightVirtualFile virtualFile = createFile();
    PsiFile file = getPsiManager().findFile(virtualFile);

    final Document document = getPsiDocumentManager().getDocument(file);

    File temp = createTempDirectory();
    final Project alienProject = createProject(new File(temp, "alien.ipr"), DebugUtil.currentStackTrace());
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
      assertEquals(0, alienDocManager.getUncommittedDocuments().length);
      assertEquals(0, getPsiDocumentManager().getUncommittedDocuments().length);

      WriteCommandAction.runWriteCommandAction(null, new Runnable() {
        @Override
        public void run() {
          document.setText("xxx");
          assertOrderedEquals(getPsiDocumentManager().getUncommittedDocuments(), document);
          assertOrderedEquals(alienDocManager.getUncommittedDocuments(), alienDocument);
        }
      });
      assertEquals("xxx", document.getText());
      assertEquals("xxx", alienDocument.getText());

      long t1 = System.currentTimeMillis() + TIMEOUT;
      while (!getPsiDocumentManager().isCommitted(document) && System.currentTimeMillis() < t1) {
        UIUtil.dispatchAllInvocationEvents();
      }
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
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
      @Override
      public void run() {
        if (commitThread.isEnabled()) {
          System.err.println("commitThread: "+commitThread + ";\n"+commitThread.log+";\n"+ThreadDumper.dumpThreadsToString());
        }
        assertFalse(commitThread.isEnabled());
        WriteCommandAction.runWriteCommandAction(null, new Runnable() {
          @Override
          public void run() {
            assertFalse(commitThread.isEnabled());
          }
        });
        assertFalse(commitThread.isEnabled());
      }
    });
    assertTrue(commitThread.isEnabled());
  }

  public void testFileChangesToText() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    Document document = getPsiDocumentManager().getDocument(psiFile);

    rename(vFile, "a.xml");
    assertFalse(psiFile.isValid());
    assertNotSame(psiFile, getPsiManager().findFile(vFile));
    psiFile = getPsiManager().findFile(vFile);

    assertSame(document, FileDocumentManager.getInstance().getDocument(vFile));
    assertSame(document, getPsiDocumentManager().getDocument(psiFile));
  }

  public void testFileChangesToBinary() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    Document document = getPsiDocumentManager().getDocument(psiFile);

    rename(vFile, "a.zip");
    assertFalse(psiFile.isValid());
    psiFile = getPsiManager().findFile(vFile);
    assertInstanceOf(psiFile, PsiBinaryFile.class);

    assertNoFileDocumentMapping(vFile, psiFile, document);
    assertEquals("abc", document.getText());
  }

  public void testFileBecomesTooLarge() throws Exception {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    Document document = getPsiDocumentManager().getDocument(psiFile);

    makeFileTooLarge(vFile);
    assertFalse(psiFile.isValid());
    psiFile = getPsiManager().findFile(vFile);
    assertInstanceOf(psiFile, PsiLargeFile.class);

    assertNoFileDocumentMapping(vFile, psiFile, document);
    assertEquals("abc", document.getText());
  }

  private void assertNoFileDocumentMapping(VirtualFile vFile, PsiFile psiFile, Document document) {
    assertNull(FileDocumentManager.getInstance().getDocument(vFile));
    assertNull(FileDocumentManager.getInstance().getFile(document));
    assertNull(getPsiDocumentManager().getPsiFile(document));
    assertNull(getPsiDocumentManager().getDocument(psiFile));
  }

  private void makeFileTooLarge(final VirtualFile vFile) throws Exception {
    WriteCommandAction.runWriteCommandAction(myProject, new ThrowableComputable<Object, Exception>() {
      @Override
      public Object compute() throws Exception {
        VfsUtil.saveText(vFile, StringUtil.repeat("a", FileUtilRt.LARGE_FOR_CONTENT_LOADING + 1));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        return null;
      }
    });
  }
}
