package org.jetbrains.javafx.testUtils;

import com.intellij.openapi.application.PathManager;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxTestUtil {
  private JavaFxTestUtil() {
  }

  public static String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/JavaFX/testData/";
  }
}
