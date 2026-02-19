// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.LocalInspectionEP;
import com.intellij.lang.LanguageExtensionPoint;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.jetbrains.idea.devkit.inspections.metaInformation.UnknownIdInMetaInformationInspection;

@TestDataPath("$CONTENT_ROOT/testData/inspections/metaInformation")
public class UnknownIdInMetaInformationInspectionTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/metaInformation";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("core-api", PathUtil.getJarPathForClass(LanguageExtensionPoint.class));
    moduleBuilder.addLibrary("analysis-api", PathUtil.getJarPathForClass(LocalInspectionEP.class));
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(UnknownIdInMetaInformationInspection.class);
  }

  public void testUnknownIds() {
    myFixture.copyDirectoryToProject("", "");
    myFixture.testHighlighting("inspectionDescriptions/metaInformation.json");
  }
}
