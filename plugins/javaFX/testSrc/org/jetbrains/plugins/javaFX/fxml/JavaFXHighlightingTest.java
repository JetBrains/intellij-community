package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.application.PluginPathManager;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 1/10/13
 */
public class JavaFXHighlightingTest extends LightDaemonAnalyzerTestCase {
  public void testLoginForm() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(getTestName(true) + ".fxml", false, false);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("javaFX") + "/testData/highlighting/";
  }
}
