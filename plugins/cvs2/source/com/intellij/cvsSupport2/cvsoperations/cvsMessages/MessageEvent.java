package com.intellij.cvsSupport2.cvsoperations.cvsMessages;

/**
 * author: lesya
 */
public class MessageEvent {
  private final String myMessage;
  private final boolean myIsError;
  private final boolean myIsTagged;

  public MessageEvent(String message, boolean isError, boolean isTagged) {
    myMessage = message;
    myIsError = isError;
    myIsTagged = isTagged;
  }

  public String getMessage() {
    return myMessage;
  }

  public boolean isError() {
    return myIsError;
  }

  public boolean isTagged() {
    return myIsTagged;
  }
}
