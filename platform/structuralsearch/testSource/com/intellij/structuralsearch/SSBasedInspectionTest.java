package com.intellij.structuralsearch;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.structuralsearch.inspection.highlightTemplate.SSBasedInspection;
import com.intellij.structuralsearch.plugin.ui.Configuration;
import com.intellij.structuralsearch.plugin.ui.SearchConfiguration;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.util.ArrayList;
import java.util.List;

public class SSBasedInspectionTest extends InspectionTestCase {
  private LocalInspectionToolWrapper myWrapper;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    SSBasedInspection inspection = new SSBasedInspection();
    List<Configuration> configurations = new ArrayList<>();

    SearchConfiguration configuration1 = new SearchConfiguration();
    MatchOptions options = configuration1.getMatchOptions();
    options.setFileType(StdFileTypes.JAVA);
    options.setSearchPattern("int i;");
    configurations.add(configuration1);

    SearchConfiguration configuration2 = new SearchConfiguration();
    options = configuration2.getMatchOptions();
    options.setFileType(StdFileTypes.JAVA);
    options.setSearchPattern("f();");
    configurations.add(configuration2);

    inspection.setConfigurations(configurations, myProject);
    myWrapper = new LocalInspectionToolWrapper(inspection);
  }

  public void testSimple() {
    doTest();
  }

  private void doTest() {
    doTest("ssBased/" + getTestName(true), myWrapper,"java 1.5");
  }

  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/";
  }
}
