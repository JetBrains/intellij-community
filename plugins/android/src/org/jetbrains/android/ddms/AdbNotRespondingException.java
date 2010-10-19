package org.jetbrains.android.ddms;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.android.util.AndroidBundle;

/**
 * @author Eugene.Kudelevsky
 */
public class AdbNotRespondingException extends Exception {
  private final String myMessage;

  public AdbNotRespondingException() {
    String processName = SystemInfo.isWindows ? "adb.exe" : "adb";
    myMessage = AndroidBundle.message("android.debug.bridge.crashed.error", processName);
  }

  public String getMessage() {
    return myMessage;
  }
}
