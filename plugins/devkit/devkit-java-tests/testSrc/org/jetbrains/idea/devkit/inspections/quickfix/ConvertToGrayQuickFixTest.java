// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.quickfix;

import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.idea.devkit.inspections.quickfix.ConvertToGrayFixture.CONVERT_TO_GRAY_FIX_NAME_PATTERN;
import static org.jetbrains.idea.devkit.inspections.quickfix.ConvertToGrayFixtureKt.convertToGrayFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspections/useGrayFix")
@TestApplication
public class ConvertToGrayQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<ConvertToGrayFixture> gray =
    convertToGrayFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/useGrayFix");

  @Test
  public void testUseGrayConstantFixInConstant() {
    gray.get().doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(125));
  }

  @Test
  public void testUseGrayConstantFixInLocalVariable() {
    gray.get().doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(25));
  }

  @Test
  public void testUseGrayConstantFixInMethodParam() {
    gray.get().doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(125));
  }

  @Test
  public void testUseGrayConstantFixInJBColorParam() {
    gray.get().doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(25));
  }

  @Test
  public void testUseGrayConstantFixWhenNumberConstantsReferenced() {
    gray.get().doTest(CONVERT_TO_GRAY_FIX_NAME_PATTERN.formatted(125));
  }
}
