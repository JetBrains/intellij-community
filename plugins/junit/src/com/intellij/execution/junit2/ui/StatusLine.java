package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.*;
import com.intellij.execution.junit2.ui.model.CompletionEvent;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.StateEvent;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.util.ColorProgressBar;

import javax.swing.*;
import java.awt.*;

class StatusLine extends JPanel {
  private final ColorProgressBar myProgressBar = new ColorProgressBar();
  private final JLabel myState = new JLabel(ExecutionBundle.message("junit.runing.info.starting.label"));
  private final StateInfo myStateInfo = new StateInfo();
  private boolean myTestsBuilt = false;

  public StatusLine() {
    super(new GridLayout(1, 2));
    add(myState);
    final JPanel progressPanel = new JPanel(new GridBagLayout());
    add(progressPanel);
    progressPanel.add(myProgressBar, new GridBagConstraints(0, 0, 0, 0, 1, 1, GridBagConstraints.CENTER, GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0));
  }

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
      String testCount;
      if (myDefects > 0)
        testCount = ExecutionBundle.message("junit.runing.info.status.completed.from.total.failed", myCompleted, myTotal, myDefects); // += "    Failed: " + myDefects + "   ";
      else
        testCount = ExecutionBundle.message("junit.runing.info.status.completed.from.total", myCompleted, myTotal); // myCompleted + " of " + myTotal
      final StringBuffer buffer = new StringBuffer();
      if (myDoneEvent != null) {
        String termMessage = generateTermMessage(testCount);
        buffer.append(termMessage);
        final String comment = myDoneEvent.getComment();
        if (comment.length() > 0) {
          buffer.append("(" + comment + ")");
        }
      } else {
        buffer.append(ExecutionBundle.message("junit.runing.info.status.running.number.with.name", testCount, myCurrentTestName));
      }
      label.setText(buffer.toString());
    }

    private String generateTermMessage(final String testCount) {
      switch(myDoneEvent.getType()) {
        case DONE: return ExecutionBundle.message("junit.runing.info.status.done.count", testCount);
        default: return ExecutionBundle.message("junit.runing.info.status.terminated.count", testCount);
      }
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
