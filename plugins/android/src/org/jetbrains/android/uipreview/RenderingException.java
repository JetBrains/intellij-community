package org.jetbrains.android.uipreview;

/**
 * @author Eugene.Kudelevsky
 */
public class RenderingException extends Exception {
  private final String myPresentableMessage;

  public RenderingException() {
    super();
    myPresentableMessage = null;
  }

  public RenderingException(String message) {
    super(message);
    myPresentableMessage = message;
  }

  public RenderingException(String message, Throwable cause) {
    super(message, cause);
    myPresentableMessage = message;
  }

  public RenderingException(Throwable cause) {
    super(cause);
    myPresentableMessage = null;
  }

  public String getPresentableMessage() {
    return myPresentableMessage;
  }
}
