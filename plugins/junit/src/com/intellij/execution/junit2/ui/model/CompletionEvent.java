package com.intellij.execution.junit2.ui.model;

import com.intellij.execution.junit2.ui.Formatters;
import com.intellij.execution.ExecutionBundle;

public class CompletionEvent extends StateEvent {
  private final boolean myNormalExit;

  public CompletionEvent(final boolean normalExit, final long time) {
    super(normalExit ? TerminatedType.DONE: TerminatedType.TERNINATED, 
          time >= 0 ? Formatters.printTime(time) : "");
    myNormalExit = normalExit;
  }

  public boolean isRunning() {
    return false;
  }

  public boolean isNormalExit() {
    return myNormalExit;
  }

  public boolean isTerminated() {
    return !myNormalExit;
  }
}
