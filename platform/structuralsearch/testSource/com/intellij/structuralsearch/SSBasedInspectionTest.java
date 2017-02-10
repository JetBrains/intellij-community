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
    SearchConfiguration configuration = new SearchConfiguration();
    MatchOptions options = new MatchOptions();
    options.setFileType(StdFileTypes.JAVA);
    options.setSearchPattern("int i;");
    configuration.setMatchOptions(options);
    configurations.add(configuration);
    configuration = new SearchConfiguration();
    options = new MatchOptions();
    options.setFileType(StdFileTypes.JAVA);
    options.setSearchPattern("f();");
    configuration.setMatchOptions(options);
    configurations.add(configuration);
    inspection.setConfigurations(configurations, myProject);
    myWrapper = new LocalInspectionToolWrapper(inspection);
  }

  public void testSimple() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest("ssBased/" + getTestName(true), myWrapper,"java 1.5");
  }

  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath() + "/platform/structuralsearch/testData/";
  }
}
