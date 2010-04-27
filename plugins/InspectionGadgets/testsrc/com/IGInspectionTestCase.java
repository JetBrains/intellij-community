package com;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author Alexey
 */
public abstract class IGInspectionTestCase extends InspectionTestCase {

  @SuppressWarnings({"JUnitTestCaseWithNonTrivialConstructors"})
  public IGInspectionTestCase() {
//    System.setProperty("idea.platform.prefix", "Idea");
  }

  protected String getTestDataPath() {
      return PluginPathManager.getPluginHomePath("InspectionGadgets") + "/test";
  }

  public void doTest(@NonNls final String folderName, final LocalInspectionTool tool) throws Exception {
    super.doTest(folderName, new LocalInspectionToolWrapper(tool), "java 1.5");
  }
}
