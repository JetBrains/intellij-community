// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.availability;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.FileContentUtilCore;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.Language;

import java.io.IOException;
import java.util.Collections;

public class PsiAvailabilityServiceTest extends PlatformTestCase {

  private PsiDocumentManager getDocManager() {
    return PsiDocumentManager.getInstance(getProject());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    UIUtil.dispatchAllInvocationEvents();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      LaterInvocator.leaveAllModals();
    }
    finally {
      super.tearDown();
    }
  }

  public void testMakeAvailableAfterDocumentChange() throws IOException {
    Document document = createPsiAndGetDocument();
    ApplicationManager.getApplication().runWriteAction(() -> document.setText("yyy"));
    assertCommittedAndParsed(document, false, true);
    assertTrue(PsiAvailabilityService.getInstance(getProject()).makePsiAvailable(document, ""));
    assertCommittedAndParsed(document, true, true);
  }

  public void testMakeAvailableAfterReparse() throws IOException {
    Document document = createPsiAndGetDocument();
    PsiFile psiFile = getDocManager().getPsiFile(document);
    reparse(psiFile.getVirtualFile());

    assertCommittedAndParsed(document, true, false);
    assertTrue(PsiAvailabilityService.getInstance(getProject()).makePsiAvailable(document, ""));
    assertCommittedAndParsed(document, true, true);
  }

  public void testMakeAvailableAfterViewProviderReset() throws IOException {
    Document document = createPsiAndGetDocument();
    PsiFile psiFile = getDocManager().getPsiFile(document);
    final VirtualFile virtualFile = psiFile.getVirtualFile();
    ApplicationManager.getApplication().runWriteAction
      (() -> PsiManagerEx.getInstanceEx(myProject).getFileManager().setViewProvider(virtualFile, null));

    assertCommittedAndParsed(document, true, false);
    assertTrue(PsiAvailabilityService.getInstance(getProject()).makePsiAvailable(document, ""));
    assertCommittedAndParsed(document, true, true);
  }

  public void testPerformWhenAvailableAfterDocumentChange() throws IOException {
    Document document = createPsiAndGetDocument();
    ApplicationManager.getApplication().runWriteAction(() -> document.setText("yyy"));
    assertCommittedAndParsed(document, false, true);

    CallBackAsserter asserter = new CallBackAsserter(document);
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, null);
    getDocManager().commitAllDocuments();
    UIUtil.dispatchAllInvocationEvents();
    asserter.assertCalledBack();
  }

  public void testPerformWhenAvailableAfterReparse() throws IOException {
    Document document = createPsiAndGetDocument();
    reparse(getDocManager().getPsiFile(document).getVirtualFile());
    assertCommittedAndParsed(document, true, false);

    CallBackAsserter asserter = new CallBackAsserter(document);
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, null);
    asserter.waitForCallBack();
  }

  public void testCalledBackImmediatelyWhenAllReady() throws IOException {
    Document document = createPsiAndGetDocument();
    assertCommittedAndParsed(document, true, true);
    CallBackAsserter asserter = new CallBackAsserter(document);
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, null);
    asserter.assertCalledBack();
  }

  public void testCancelImmediately() throws IOException {
    Document document = createPsiAndGetDocument();
    reparse(getDocManager().getPsiFile(document).getVirtualFile());
    assertCommittedAndParsed(document, true, false);

    CallBackAsserter asserter = new CallBackAsserter(document);
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, indicator);
    indicator.cancel();
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, null);
    asserter.waitForCallBack();
  }

  public void testCancelAfterCommit() throws IOException {
    Document document = createPsiAndGetDocument();
    reparse(getDocManager().getPsiFile(document).getVirtualFile());
    assertCommittedAndParsed(document, true, false);

    CallBackAsserter asserter = new CallBackAsserter(document);
    EmptyProgressIndicator indicator = new EmptyProgressIndicator();
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, indicator);
    getDocManager().commitDocument(document);
    indicator.cancel();
    PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, null);
    asserter.waitForCallBack();
  }

  //public void testCancelDuringLongReparse() throws IOException {
  //  Document document = createHugeJavaFile();
  //  getDocManager().commitDocument(document);
  //  reparse(getDocManager().getPsiFile(document).getVirtualFile());
  //  assertCommittedAndParsed(document, true, false);
  //
  //  CallBackAsserter asserter = new CallBackAsserter(document);
  //  EmptyProgressIndicator indicator = new EmptyProgressIndicator();
  //  PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, indicator);
  //  UIUtil.dispatchAllInvocationEvents();
  //  TimeoutUtil.sleep(10);
  //  indicator.cancel();
  //  PsiAvailabilityService.getInstance(getProject()).performWhenPsiAvailable(document, asserter, null);
  //  asserter.waitForCallBack();
  //}


  private class CallBackAsserter implements Runnable {
    final Document document;
    boolean calledBack = false;

    private CallBackAsserter(Document document) {
      this.document = document;
    }

    @Override
    public void run() {
      ApplicationManager.getApplication().assertIsDispatchThread();
      assertCommittedAndParsed(document, true, true);
      assertFalse("Called back second time", calledBack);
      calledBack = true;
    }

    void assertCalledBack() {
      assertTrue(calledBack);
    }

    public void waitForCallBack() {
      int retries = 0;
      //noinspection WhileLoopSpinsOnField
      while (!calledBack) {
        UIUtil.dispatchAllInvocationEvents();
        TimeoutUtil.sleep(50);
        assertTrue(retries < 50);
        retries++;
      }

      checkThereIsNoSecondCallBack();
    }

    private void checkThereIsNoSecondCallBack() {
      for (int i = 0; i < 5; ++i) {
        UIUtil.dispatchAllInvocationEvents();
        TimeoutUtil.sleep(50);
      }
    }
  }

  private void assertCommittedAndParsed(Document document, boolean committed, boolean parsed) {
    assertEquals(committed, getDocManager().isCommitted(document));
    PsiFile file = getDocManager().getPsiFile(document);
    assertEquals(parsed, file.getNode().isParsed());
  }

  private static void reparse(VirtualFile vFile) {
    ApplicationManager.getApplication().runWriteAction(
      () -> FileContentUtilCore.reparseFiles(Collections.singleton(vFile)));
  }

  private Document createPsiAndGetDocument() throws IOException {
    VirtualFile vFile = getVirtualFile(createTempFile("a.txt", "abc"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    Document document = getDocManager().getDocument(psiFile);
    assertNotNull(document);
    getDocManager().commitDocument(document);
    psiFile.getNode().getFirstChildNode();
    return document;
  }

  private Document createHugeJavaFile() throws IOException {
    @Language(value = "JAVA", prefix = "class c {", suffix = "}")
    String body = "@NotNull\n" +
                  "  private static String getTooLargeContent() {\n" +
                  "    return StringUtil.repeat(\"a\", FileUtilRt.LARGE_FOR_CONTENT_LOADING + 1);\n" +
                  "  }\n" +
                  "private abstract static class FileTooBigExceptionCase extends AbstractExceptionCase {\n" +
                  "    @Override\n" +
                  "    public Class getExpectedExceptionClass() {\n" +
                  "      return FileTooBigException.class;\n" +
                  "    }\n" +
                  "  }";
    VirtualFile vFile = getVirtualFile(createTempFile("a.java", "class c {" + StringUtil.repeat(body, 10000) + "}"));
    PsiFile psiFile = getPsiManager().findFile(vFile);
    assertEquals(StdFileTypes.JAVA, psiFile.getFileType());
    Document document = getDocManager().getDocument(psiFile);

    ApplicationManager.getApplication().runWriteAction(() -> {
      document.setText("class c {" + StringUtil.repeat(body, 10000) + "}");
    });

    return document;
  }
}
