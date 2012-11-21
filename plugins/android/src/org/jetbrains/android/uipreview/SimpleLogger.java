package org.jetbrains.android.uipreview;
import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
class SimpleLogger extends LayoutLog implements ILogger {
  private final Logger myLog;
  private final List<FixableIssueMessage> myMessages = new ArrayList<FixableIssueMessage>();
  private final Project myProject;

  public SimpleLogger(@Nullable Project project, @NotNull Logger log) {
    myProject = project;
    myLog = log;
  }

  @Override
  public void error(String tag, String message, Object data) {
    final String s = getFullMessage(tag, message);
    myLog.debug(s);
    myMessages.add(new FixableIssueMessage(s));
  }

  @Override
  public void error(String tag, String message, Throwable throwable, Object data) {
    myLog.debug(throwable);
    final String s = getFullMessage(tag, message);
    myLog.debug(s);

    if (myProject != null) {
      if (throwable != null) {
        myMessages.add(FixableIssueMessage.createExceptionIssue(myProject, s, throwable));
      }
      else {
        myMessages.add(new FixableIssueMessage(s));
      }
    }
  }

  @Override
  public void warning(String tag, String message, Object data) {
    final String s = getFullMessage(tag, message);
    myLog.debug(s);
    myMessages.add(new FixableIssueMessage(s));
  }

  @Override
  public void fidelityWarning(String tag, String message, Throwable throwable, Object data) {
    myLog.debug(throwable);
    final String s = getFullMessage(tag, message);
    myLog.debug(s);

    if (myProject != null) {
      if (throwable != null) {
        myMessages.add(FixableIssueMessage.createExceptionIssue(myProject, s, throwable));
      }
      else {
        myMessages.add(new FixableIssueMessage(s));
      }
    }
  }

  @Override
  public void warning(String warningFormat, Object... args) {
    final String s = String.format(warningFormat, args);
    myLog.debug(s);
    myMessages.add(new FixableIssueMessage(s));
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    if (msgFormat != null) {
      myLog.debug(String.format(msgFormat, args));
    }
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
  }

  @Override
  public void error(Throwable t, String errorFormat, Object... args) {
    myLog.debug(t);
    final String s = String.format(errorFormat, args);
    myLog.debug(s);

    if (myProject != null) {
      if (t != null) {
        myMessages.add(FixableIssueMessage.createExceptionIssue(myProject, s, t));
      }
      else {
        myMessages.add(new FixableIssueMessage(s));
      }
    }
  }

  @NotNull
  private static String getFullMessage(String tag, String message) {
    if (message == null) {
      message = "UNKNOWN";
    }
    return tag != null ? tag + ": " + message : message;
  }

  @NotNull
  public List<FixableIssueMessage> getMessages() {
    return myMessages;
  }
}
