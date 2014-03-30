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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.junit2.*;
import com.intellij.execution.junit2.events.NewChildEvent;
import com.intellij.execution.junit2.events.StateChangedEvent;
import com.intellij.execution.junit2.events.TestEvent;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.ui.TestStatusLine;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ColorProgressBar;

import javax.swing.*;

class JUnitStatusLine extends TestStatusLine {
  private final StateInfo myStateInfo = new StateInfo();
  private boolean myTestsBuilt = false;

  public void setModel(final JUnitRunningModel model) {
    myTestsBuilt = true;
    myProgressBar.setColor(ColorProgressBar.GREEN);
    model.addListener(new TestProgressListener(model.getProgress()));
  }

  public void onProcessStarted(final ProcessHandler process) {
    if (myTestsBuilt) return;
    process.addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        process.removeProcessListener(this);
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myStateInfo.setTerminated(myState);
            if (!myTestsBuilt && myProgressBar.getFraction() == 0.0) {
              myProgressBar.setColor(ColorProgressBar.RED);
              myProgressBar.setFraction(1.0);
              myState.setText(ExecutionBundle.message("junit.runing.info.failed.to.start.error.message"));
            }
          }
        });
      }
    });
  }

  private static class StateInfo {
    private int myTotal = 0;
    private int myCompleted = 0;
    private int myDefects = 0;
    private String myCurrentTestName = "";
    private StateEvent myDoneEvent;
    private boolean myTerminated = false;

    public void setDone(final StateEvent event) {
      myDoneEvent = event;
    }

    public void updateCounters(final TestProgress progress) {
      myTotal = progress.getMaximum();
      myCompleted = progress.getValue();
      myDefects = progress.countDefects();
      final TestProxy currentTest = progress.getCurrentTest();
      myCurrentTestName = currentTest == null ? "" : Formatters.printTest(currentTest);
    }

    public double getComplitedPercents() {
      return (double)myCompleted/(double)myTotal;
    }

    public void updateLabel(final JLabel label) {
      final StringBuffer buffer = new StringBuffer();
      if (myDoneEvent != null && myTerminated) {
        String termMessage = generateTermMessage(getTestCount(0));
        buffer.append(termMessage);
        final String comment = myDoneEvent.getComment();
        if (comment.length() > 0) {
          buffer.append(" (" + comment + ")");
        }
      } else {
        buffer.append(ExecutionBundle.message("junit.runing.info.status.running.number.with.name", getTestCount(myDoneEvent != null ? 0 : 1), myCurrentTestName));
      }
      label.setText(buffer.toString());
    }

    private String getTestCount(int offset) {
      String testCount;
      if (myDefects > 0)
        testCount = ExecutionBundle.message("junit.runing.info.status.completed.from.total.failed", myCompleted + offset, myTotal, myDefects); // += "    Failed: " + myDefects + "   ";
      else
        testCount = ExecutionBundle.message("junit.runing.info.status.completed.from.total", myCompleted + offset, myTotal); // myCompleted + " of " + myTotal
      return testCount;
    }

    private String generateTermMessage(final String testCount) {
      switch(myDoneEvent.getType()) {
        case DONE: return ExecutionBundle.message("junit.runing.info.status.done.count", testCount);
        default: return ExecutionBundle.message("junit.runing.info.status.terminated.count", testCount);
      }
    }

    public void setTerminated(JLabel stateLabel) {
      myTerminated = true;
      updateLabel(stateLabel);
    }
  }

  private class TestProgressListener extends JUnitAdapter {
    private TestProgress myProgress;

    public TestProgressListener(final TestProgress progress) {
      myProgress = progress;
    }

    public void onRunnerStateChanged(final StateEvent event) {
      if (!event.isRunning()) {
        final CompletionEvent completionEvent = (CompletionEvent) event;
        myStateInfo.setDone(completionEvent);
        myProgress.setDone(completionEvent);
        if (completionEvent.isTerminated() && !myProgress.hasDefects()) {
          myProgressBar.setColor(ColorProgressBar.YELLOW);
        }
        updateCounters();
      }
    }

    public void onTestChanged(final TestEvent event) {
      if (event instanceof StateChangedEvent || event instanceof NewChildEvent)
        updateCounters();
    }

    public void doDispose() {
      myProgress = null;
    }

    private void updateCounters() {
      myStateInfo.updateCounters(myProgress);
      myProgressBar.setFraction(myStateInfo.getComplitedPercents());
      if (myProgress.hasDefects()) {
        myProgressBar.setColor(ColorProgressBar.RED);
      }
      myStateInfo.updateLabel(myState);
    }
  }
}
