package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderingException extends Exception {
  private final String myPresentableMessage;
  private final Throwable[] myCauses;
  private List<FixableIssueMessage> myWarnMessages;

  public RenderingException() {
    super();
    myPresentableMessage = null;
    myCauses = new Throwable[0];
  }

  public RenderingException(String message, Throwable... causes) {
    super(message, causes.length > 0 ? causes[0] : null);
    myPresentableMessage = message;
    myCauses = causes;
  }

  public RenderingException(@NotNull Throwable... causes) {
    myPresentableMessage = null;
    myCauses = causes;
  }

  @NotNull
  public Throwable[] getCauses() {
    return myCauses;
  }

  public String getPresentableMessage() {
    return myPresentableMessage;
  }

  public List<FixableIssueMessage> getWarnMessages() {
    return myWarnMessages;
  }

  public RenderingException setWarnMessages(List<FixableIssueMessage> warnMessages) {
    myWarnMessages = warnMessages;
    return this;
  }

  @Override
  public void printStackTrace(PrintStream s) {
    if (myCauses.length > 0) {
      for (Throwable e : myCauses) {
        e.printStackTrace(s);
      }
    }
    else {
      super.printStackTrace(s);
    }
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    if (myCauses.length > 0) {
      for (Throwable e : myCauses) {
        e.printStackTrace(s);
      }
    }
    else {
      super.printStackTrace(s);
    }
  }
}
