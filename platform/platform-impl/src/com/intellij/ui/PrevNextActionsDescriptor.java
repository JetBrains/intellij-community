/*
 * @author max
 */
package com.intellij.ui;

public class PrevNextActionsDescriptor {
  private final String myPrevActionId;
  private final String myNextActionId;

  public PrevNextActionsDescriptor(final String nextActionId, final String prevActionId) {
    myNextActionId = nextActionId;
    myPrevActionId = prevActionId;
  }

  public String getNextActionId() {
    return myNextActionId;
  }

  public String getPrevActionId() {
    return myPrevActionId;
  }
}