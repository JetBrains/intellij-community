package com;

import com.intellij.testFramework.InspectionTestCase;
import com.intellij.openapi.application.PathManager;

/**
 * @author Alexey
 */
public abstract class IGInspectionTestCase extends InspectionTestCase {
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/InspectionGadgets/test";
  }
}
