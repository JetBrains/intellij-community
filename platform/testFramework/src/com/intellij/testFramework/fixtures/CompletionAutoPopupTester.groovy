/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.testFramework.fixtures
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.UsefulTestCase
import com.intellij.util.ui.UIUtil

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
/**
 * @author peter
 */
class CompletionAutoPopupTester {
  private final CodeInsightTestFixture myFixture

  CompletionAutoPopupTester(CodeInsightTestFixture fixture) {
    myFixture = fixture
  }

  void runWithAutoPopupEnabled(Runnable r) {
    assert !ApplicationManager.application.isDispatchThread()
    CompletionAutoPopupHandler.ourTestingAutopopup = true
    try {
      r.run()
    }
    finally {
      CompletionAutoPopupHandler.ourTestingAutopopup = false
      ((DocumentEx) myFixture.editor.document).setModificationStamp(0)  // to not let autopopup handler sneak in
    }
  }

  void joinCompletion() {
    for (j in 1..4000) {
      LookupImpl l = null
      UsefulTestCase.edt {
        l = lookup
      }
      if (!l || !l.calculating) {
        UsefulTestCase.edt {} // for invokeLater in CompletionProgressIndicator.stop()
        return
      }
      Thread.sleep(10)
    }
    UsefulTestCase.printThreadDump()
    UsefulTestCase.fail("Too long completion")
  }

  final static AtomicInteger cnt = new AtomicInteger()
  def joinCommit(Closure c1={}) {
    final AtomicBoolean committed = new AtomicBoolean()
    final AtomicBoolean run = new AtomicBoolean()
    boolean executed=true;
    def closureSeq = cnt.getAndIncrement()
    Runnable r = new Runnable() {
      @Override
      public void run() {
        run.set(true)
        ApplicationManager.application.invokeLater {
          c1();
          committed.set(true)
        };
      }

      @Override
      public String toString() {
        return "Closure "+closureSeq;
      }
    };
    UsefulTestCase.edt {
      executed = PsiDocumentManager.getInstance(myFixture.project).performWhenAllCommitted(r);
    }
    assert !ApplicationManager.getApplication().isWriteAccessAllowed()
    assert !ApplicationManager.getApplication().isReadAccessAllowed()
    assert !ApplicationManager.getApplication().isDispatchThread()
    def start = System.currentTimeMillis()
    while (!committed.get()) {
      if (System.currentTimeMillis() - start >= 20000) {
        UsefulTestCase.fail("too long waiting for documents to be committed. executed: $executed; r: $r; run: $run; ")
        UsefulTestCase.printThreadDump()
      }
      UIUtil.pump();
    }
  }

  void joinAutopopup() {
    joinAlarm();
    joinCommit() // physical document commit
    joinCommit() // file copy commit in background
  }

  def joinAlarm() {
    AutoPopupController.getInstance(myFixture.getProject()).executePendingRequests()
  }

  LookupImpl getLookup() {
    (LookupImpl)LookupManager.getInstance(myFixture.project).getActiveLookup()
  }

  void typeWithPauses(String s) {
    for (i in 0..<s.size()) {
      final c = s.charAt(i)
      myFixture.type(c)
      joinAutopopup() // for the autopopup handler's alarm, or the restartCompletion's invokeLater
      joinCompletion()
    }
  }

}
