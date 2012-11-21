package org.jetbrains.android.uipreview;

import com.android.annotations.NonNull;
import com.android.ide.common.rendering.api.LayoutLog;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;

/**
* @author Eugene.Kudelevsky
*/
class SimpleLogger extends LayoutLog implements ILogger {
  private final Logger myLog;

  public SimpleLogger(Logger log) {
    myLog = log;
  }

  @Override
  public void error(String tag, String message, Object data) {
    myLog.debug(tag + ": " + message);
  }

  @Override
  public void error(String tag, String message, Throwable throwable, Object data) {
    myLog.debug(throwable);
    myLog.debug(tag + ": " + message);
  }

  @Override
  public void warning(String tag, String message, Object data) {
    myLog.debug(tag + ": " + message);
  }

  @Override
  public void fidelityWarning(String tag, String message, Throwable throwable, Object data) {
    myLog.debug(throwable);
    myLog.debug(tag + ": " + message);
  }

  @Override
  public void warning(String warningFormat, Object... args) {
    myLog.debug(String.format(warningFormat, args));
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    myLog.debug(String.format(msgFormat, args));
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
  }

  @Override
  public void error(Throwable t, String errorFormat, Object... args) {
    myLog.debug(t);
    myLog.debug(String.format(errorFormat, args));
  }
}
