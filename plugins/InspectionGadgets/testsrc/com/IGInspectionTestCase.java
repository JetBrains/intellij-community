package com;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author Alexey
 */
public abstract class IGInspectionTestCase extends InspectionTestCase {
  protected String getTestDataPath() {
      return PathManager.getHomePath() + "/svnPlugins/InspectionGadgets/test";
  }

  public void doTest(@NonNls final String folderName, final LocalInspectionTool tool) throws Exception {
    super.doTest(folderName, new LocalInspectionToolWrapper(tool), "java 1.5");
  }
}
