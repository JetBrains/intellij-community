package com.intellij.codeInspection.i18n;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.impl.ModuleManagerImpl;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.InspectionTestCase;

public class InvalidPropertyKeyInspectionTest extends InspectionTestCase {
  private void doTest() throws Exception {
    LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(new InvalidPropertyKeyInspection());
    doTest("invalidPropertyKey/" + getTestName(true), tool, "java 1.5");
  }

  @Override
  protected void setupRootModel(final String testDir, final VirtualFile[] sourceDir, final String jdkName) {
    super.setupRootModel(testDir, sourceDir, jdkName);
    ((ModuleManagerImpl)ModuleManager.getInstance(getProject())).projectOpened();
    ((StartupManagerImpl)StartupManager.getInstance(getProject())).runPostStartupActivities();
  }

  public void testSimple() throws Exception {
    doTest();
  }

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("java-i18n") + "/testData/inspections";
  }
}
