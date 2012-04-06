package org.jetbrains.android.uipreview;

import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderingException extends Exception {
  private final String myPresentableMessage;
  private final Throwable[] myCauses;

  public RenderingException() {
    super();
    myPresentableMessage = null;
    myCauses = new Throwable[0];
  }

  public RenderingException(String message) {
    super(message);
    myPresentableMessage = message;
    myCauses = new Throwable[0];
  }

  public RenderingException(String message, Throwable cause) {
    super(message, cause);
    myPresentableMessage = message;
    myCauses = new Throwable[]{cause};
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

  @Override
  public void printStackTrace(PrintStream s) {
    if (myCauses != null && myCauses.length > 0) {
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
    if (myCauses != null && myCauses.length > 0) {
      for (Throwable e : myCauses) {
        e.printStackTrace(s);
      }
    }
    else {
      super.printStackTrace(s);
    }
  }
}
