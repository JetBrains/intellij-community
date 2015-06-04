package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.DaemonAnalyzerTestCase;
import com.intellij.openapi.util.SystemInfo;
import org.junit.Assume;

/**
 * @author nik
 */
public class JavaFXDaemonAnalyzerTestCase extends DaemonAnalyzerTestCase {
  @Override
  protected void runTest() throws Throwable {
    Assume.assumeFalse(SystemInfo.isMac);
    super.runTest();
  }
}
