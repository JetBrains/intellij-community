// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;

@TestDataPath("$CONTENT_ROOT/testData/inspections/incorrectParentDisposable")
public class IncorrectParentDisposableInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectParentDisposable";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(IncorrectParentDisposableInspection.class);
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) throws Exception {
    moduleBuilder.addLibrary("platform-core-api", PathUtil.getJarPathForClass(Project.class));
    moduleBuilder.addLibrary("platform-util", PathUtil.getJarPathForClass(Disposer.class));
    moduleBuilder.addLibrary("platform-extensions", PathUtil.getJarPathForClass(ComponentManager.class));
  }

  public void testIncorrectDisposableProject() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIncorrectDisposableApplication() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testIncorrectDisposableModule() {
    myFixture.testHighlighting(getTestName(false) + ".java");
  }
}
