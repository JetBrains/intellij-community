package com.intellij.execution.junit2.ui.model;

public class StateEvent {

  public enum TerminatedType {
    DONE, TERNINATED
  }

  private final String myComment;

  public StateEvent(final TerminatedType type, final String comment) {
    myComment = comment;
    myTerminatedType = type;
  }

  private final TerminatedType myTerminatedType;

  public boolean isRunning() {
    return true;
  }

  public TerminatedType getType() {
    return myTerminatedType;
  }

  public String getComment() {
    return myComment;
  }
}
