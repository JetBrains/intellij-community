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
package com.intellij.xdebugger.impl.ui.tree.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.frame.XFullValueEvaluator;
import com.intellij.xdebugger.impl.ui.tree.nodes.WatchMessageNode;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * User: ksafonov
 */
public abstract class XFetchValueActionBase extends XDebuggerTreeActionBase {

  protected abstract void handle(final Project project, final String value);

  @Override
  protected void perform(final XValueNodeImpl node, @NotNull final String nodeName, final AnActionEvent e) {
    XFullValueEvaluator fullValueEvaluator = node.getFullValueEvaluator();
    if (fullValueEvaluator == null) {
      String value = node.getValue();
      handle(node.getTree().getProject(), value);
      return;
    }

    final XDebugSession session = node.getTree().getSession();
    startFetchingValue(fullValueEvaluator, session);
  }

  @Override
  protected void perform(WatchMessageNode node, AnActionEvent e) {
    handle(e.getProject(), node.getExpression());
  }

  private void startFetchingValue(@NotNull XFullValueEvaluator fullValueEvaluator,
                                  final @NotNull XDebugSession session) {
    final CopyValueEvaluationCallback callback = new CopyValueEvaluationCallback(session);
    fullValueEvaluator.startEvaluation(callback);
    new Alarm().addRequest(new Runnable() {
      @Override
      public void run() {
        callback.showProgress();
      }
    }, 500);
  }

  @Override
  protected boolean isEnabled(final XValueNodeImpl node) {
    return super.isEnabled(node) && node.getValue() != null;
  }

  @Override
  protected boolean isEnabled(WatchMessageNode node) {
    return true;
  }

  private class CopyValueEvaluationCallback implements XFullValueEvaluator.XFullValueEvaluationCallback {
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
      handle(mySession.getProject(), fullValue);
      evaluationComplete();
    }

    @Override
    public void evaluated(@NotNull String fullValue, @Nullable Font font) {
      evaluated(fullValue);
    }

    @Override
    public void errorOccurred(@NotNull String errorMessage) {
      mySession.reportError(XDebuggerBundle.message("load.value.task.error", errorMessage));
      evaluationComplete();
    }

    private void evaluationComplete() {
      myEvaluated = true;
      mySemaphore.up();
    }

    @Override
    public boolean isObsolete() {
      return myCanceled;
    }

    public void showProgress() {
      if (myEvaluated || mySession.isStopped()) return;

      new Task.Backgroundable(mySession.getProject(), XDebuggerBundle.message("load.value.task.text")) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          indicator.setIndeterminate(true);
          int i = 0;
          while (!myCanceled && !myEvaluated) {
            indicator.checkCanceled();
            indicator.setFraction(((i++) % 100) * 0.01);
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
