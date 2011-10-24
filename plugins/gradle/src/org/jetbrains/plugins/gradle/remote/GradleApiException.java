package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * We talk to Gradle API from the dedicated process, i.e. all gradle classes are loaded at that process in order to avoid
 * memory problems at IJ process. That means that if Gradle API throws an exception, it can't be correctly read at IJ process
 * (NoClassDefFoundError and ClassNotFoundException).
 * <p/>
 * This class allows to extract textual description of the target problem and deliver it for further processing without risking to 
 * get the problems mentioned above. I.e. it doesn't require anything specific can be safely delivered to IJ process then.
 * 
 * @author Denis Zhdanov
 * @since 10/21/11 11:42 AM
 */
public class GradleApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  
  private final String myOriginalReason;

  public GradleApiException() {
    this(null, null);
  }

  public GradleApiException(@Nullable String message) {
    this(message, null);
  }

  public GradleApiException(@Nullable Throwable cause) {
    this("", cause);
  }

  public GradleApiException(@Nullable String message, @Nullable Throwable cause) {
    super(extractMessage(message, cause));
    if (cause == null) {
      myOriginalReason = "";
      return;
    }
    
    StringWriter stringWriter = new StringWriter();
    PrintWriter printWriter = new PrintWriter(stringWriter);
    try {
      cause.printStackTrace(printWriter);
    }
    finally {
      printWriter.close();
    }
    myOriginalReason = stringWriter.toString();
  }
  
  /**
   * @return    textual description of the wrapped exception (if any); empty string otherwise
   */
  @NotNull
  public String getOriginalReason() {
    return myOriginalReason;
  }

  @Override
  public void printStackTrace(PrintWriter s) {
    super.printStackTrace(s);
    s.println(myOriginalReason);
  }

  @Override
  public void printStackTrace(PrintStream s) {
    super.printStackTrace(s);
    s.println(myOriginalReason);
  }

  @Nullable
  private static String extractMessage(@Nullable String message, @Nullable Throwable cause) {
    if (message != null) {
      return message;
    }
    return cause == null ? "" : cause.getMessage(); 
  }
  
}
