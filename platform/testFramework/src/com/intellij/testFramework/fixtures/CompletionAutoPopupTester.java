package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * @author peter
 */
public class CompletionAutoPopupTester {
  private final CodeInsightTestFixture myFixture;

  public CompletionAutoPopupTester(CodeInsightTestFixture fixture) {
    myFixture = fixture;
  }

  public void runWithAutoPopupEnabled(@NotNull ThrowableRunnable<Throwable> r) throws Throwable {
    assert !ApplicationManager.getApplication().isDispatchThread();
    TestModeFlags.set(CompletionAutoPopupHandler.ourTestingAutopopup, true);
    try {
      r.run();
    }
    finally {
      TestModeFlags.reset(CompletionAutoPopupHandler.ourTestingAutopopup);
      Editor editor = myFixture.getEditor();
      if (editor != null) {
        ((DocumentEx)editor.getDocument()).setModificationStamp(0);// to force possible autopopup handler's invokeLater cancel itself
      }
    }
  }

  public void joinCompletion() {
    waitPhase(phase -> !(phase instanceof CompletionPhase.CommittingDocuments ||
                         phase instanceof CompletionPhase.Synchronous ||
                         phase instanceof CompletionPhase.BgCalculation));
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  private static void waitPhase(Predicate<? super CompletionPhase> condition) {
    for (int j = 1; j < 1000; j++) {
      // in EDT to avoid getting the phase in the middle of a complex EDT operation that changes the phase several times
      CompletionPhase phase = EdtTestUtil.runInEdtAndGet(() -> CompletionServiceImpl.getCompletionPhase());
      if (condition.test(phase)) {
        return;
      }
      if (j >= 400 && j % 100 == 0) {
        System.out.println("Free memory: " + Runtime.getRuntime().freeMemory() + " of " + Runtime.getRuntime().totalMemory() + "\n");
        UsefulTestCase.printThreadDump();
        System.out.println("\n\n----------------------------\n\n");
      }

      TimeoutUtil.sleep(10);
    }

    TestCase.fail("Too long completion: " + CompletionServiceImpl.getCompletionPhase());
  }

  public void joinCommit(Runnable c1) {
    AtomicBoolean committed = new AtomicBoolean();
    AtomicBoolean run = new AtomicBoolean();
    AtomicBoolean executed = new AtomicBoolean(true);
    EdtTestUtil.runInEdtAndWait(() -> executed.set(PsiDocumentManager.getInstance(myFixture.getProject()).performWhenAllCommitted(() -> {
      run.set(true);
      ApplicationManager.getApplication().invokeLater(() -> {
        c1.run();
        committed.set(true);
      });
    })));
    assert !ApplicationManager.getApplication().isWriteAccessAllowed();
    assert !ApplicationManager.getApplication().isReadAccessAllowed();
    assert !ApplicationManager.getApplication().isDispatchThread();
    long start = System.currentTimeMillis();
    while (!committed.get()) {
      if (System.currentTimeMillis() - start >= 20000) {
        UsefulTestCase.printThreadDump();
        TestCase.fail("too long waiting for documents to be committed. executed: " + executed + "; run: " + run + "; ");
      }

      UIUtil.pump();
    }
  }

  public void joinCommit() {
    joinCommit(EmptyRunnable.getInstance());
  }

  public void joinAutopopup() {
    waitPhase(phase -> !(phase instanceof CompletionPhase.CommittingDocuments));
  }

  public LookupImpl getLookup() {
    return (LookupImpl)myFixture.getLookup();
  }

  public void typeWithPauses(String s) {
    for (int i = 0; i < s.length(); i++) {
      myFixture.type(s.charAt(i));
      joinAutopopup();// for the autopopup handler's alarm, or the restartCompletion's invokeLater
      joinCompletion();
    }
  }

}
