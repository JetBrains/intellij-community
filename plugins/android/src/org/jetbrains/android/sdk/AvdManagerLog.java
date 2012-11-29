package org.jetbrains.android.sdk;

import com.android.annotations.NonNull;
import com.android.utils.ILogger;
import com.intellij.openapi.diagnostic.Logger;

/**
* @author Eugene.Kudelevsky
*/
public class AvdManagerLog implements ILogger {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.android.sdk.AvdManagerLog");

  public void warning(String warningFormat, Object... args) {
    if (warningFormat != null) {
      LOG.debug(String.format(warningFormat, args));
    }
  }

  @Override
  public void info(@NonNull String msgFormat, Object... args) {
    if (msgFormat != null) {
      LOG.debug(String.format(msgFormat, args));
    }
  }

  @Override
  public void verbose(@NonNull String msgFormat, Object... args) {
  }

  public void error(Throwable t, String errorFormat, Object... args) {
    if (t != null) {
      LOG.debug(t);
    }
    if (errorFormat != null) {
      String message = String.format(errorFormat, args);
      LOG.debug(message);
    }
  }
}
