package org.jetbrains.javafx.testUtils;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.PluginPathManager;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxTestUtil {
  private JavaFxTestUtil() {
  }

  public static String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("JavaFX") + "/testData/";
  }
}
