/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * @author nik
 */
public class XCopyValueAction extends XDebuggerTreeActionBase {
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XFullValueEvaluator fullValueEvaluator = node.getFullValueEvaluator();
    if (fullValueEvaluator == null) {
      String value = node.getValue();
      copyToClipboard(value);
      return;
    }

    final XDebugSession session = node.getTree().getSession();
    startCopying(fullValueEvaluator, session);
  }

  private static void startCopying(@NotNull XFullValueEvaluator fullValueEvaluator, final @NotNull XDebugSession session) {
    final CopyValueEvaluationCallback callback = new CopyValueEvaluationCallback(session);
    fullValueEvaluator.startEvaluation(callback);
    new Alarm().addRequest(new Runnable() {
        @Override
        public void run() {
          callback.showProgress();
        }
      }, 500);
  }

  private static void copyToClipboard(String value) {
    CopyPasteManager.getInstance().setContents(new StringSelection(value));
  }

  protected boolean isEnabled(final XValueNodeImpl node) {
    return super.isEnabled(node) && node.getValue() != null;
  }

  private static class CopyValueEvaluationCallback implements XFullValueEvaluator.XFullValueEvaluationCallback {
    private final XDebugSession mySession;
    private volatile boolean myEvaluated;
    private volatile boolean myCanceled;
    private Semaphore mySemaphore;

    public CopyValueEvaluationCallback(XDebugSession session) {
      mySession = session;
      mySemaphore = new Semaphore();
      mySemaphore.down();
    }

    @Override
    public void evaluated(@NotNull String fullValue) {
      copyToClipboard(fullValue);
      myEvaluated = true;
      mySemaphore.up();
    }

    @Override
    public void evaluated(@NotNull String fullValue, @Nullable Font font) {
      evaluated(fullValue);
    }

    @Override
    public void errorOccurred(@NotNull String errorMessage) {
      mySession.reportError("Cannot copy value: " + errorMessage);
    }

    @Override
    public boolean isObsolete() {
      return myCanceled;
    }

    public void showProgress() {
      if (myEvaluated || mySession.isStopped()) return;

      new Task.Backgroundable(mySession.getProject(), "Copying Value") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          indicator.setText("Fetching value...");
          int i = 0;
          while (!myCanceled && !myEvaluated) {
            indicator.checkCanceled();
            indicator.setFraction(((i++)%100)*0.01);
            mySemaphore.waitFor(300);
          }
        }

        @Override
        public boolean shouldStartInBackground() {
          return false;
        }

        @Override
        public void onCancel() {
          myCanceled = true;
        }
      }.queue();
    }
  }
}
