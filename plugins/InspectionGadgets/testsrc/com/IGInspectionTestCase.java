package com;

import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author Alexey
 */
public abstract class IGInspectionTestCase extends InspectionTestCase {
  protected String getTestDataPath() {
      return PathManager.getHomePath() + "/plugins/InspectionGadgets/test";
  }
}
