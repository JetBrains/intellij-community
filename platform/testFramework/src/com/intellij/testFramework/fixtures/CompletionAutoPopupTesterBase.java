// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.completion.CompletionPhase;
import com.intellij.codeInsight.completion.impl.CompletionServiceImpl;
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.testFramework.common.ThreadUtil;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.ui.UIUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public abstract class CompletionAutoPopupTesterBase {
  public void runWithAutoPopupEnabled(@NotNull ThrowableRunnable<Throwable> r) throws Throwable {
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    try {
      TestModeFlags.runWithFlag(CompletionAutoPopupHandler.ourTestingAutopopup, true, ()->{r.run(); return null;});
    }
    finally {
      Editor editor = getEditor();
      if (editor != null) {
        ((DocumentEx)editor.getDocument()).setModificationStamp(0);// to force possible autopopup handler's invokeLater cancel itself
      }
    }
  }

  protected abstract @Nullable Editor getEditor();

  public void joinCompletion() {
    waitPhase(phase -> {
      return !(phase instanceof CompletionPhase.CommittingDocuments ||
               phase instanceof CompletionPhase.Synchronous ||
               phase instanceof CompletionPhase.BgCalculation);
    });
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
        ThreadUtil.printThreadDump();
        System.out.println("\n\n----------------------------\n\n");
      }

      TimeoutUtil.sleep(10);
    }

    TestCase.fail("Too long completion: " + CompletionServiceImpl.getCompletionPhase());
  }

  protected abstract @NotNull Project getProject();

  public void joinCommit(Runnable c1) {
    AtomicBoolean committed = new AtomicBoolean();
    AtomicBoolean run = new AtomicBoolean();
    AtomicBoolean executed = new AtomicBoolean(true);
    EdtTestUtil.runInEdtAndWait(() -> executed.set(PsiDocumentManager.getInstance(getProject()).performWhenAllCommitted(() -> {
      run.set(true);
      ApplicationManager.getApplication().invokeLater(() -> {
        c1.run();
        committed.set(true);
      });
    })));
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    ThreadingAssertions.assertNoOwnReadAccess();
    long start = System.currentTimeMillis();
    while (!committed.get()) {
      if (System.currentTimeMillis() - start >= 20000) {
        ThreadUtil.printThreadDump();
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

  public abstract LookupImpl getLookup();

  protected abstract void type(char c);

  public void typeWithPauses(String s) {
    for (int i = 0; i < s.length(); i++) {
      type(s.charAt(i));
      joinAutopopup();// for the autopopup handler's alarm, or the restartCompletion's invokeLater
      joinCompletion();
    }
  }
}
