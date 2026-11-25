package com.intellij.htmltools.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownTargetInspection;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class CreateHtmlFileFromUsageTest {
  private static final String TEST_DATA_PATH =
    PathManager.getCommunityHomePath() + "/plugins/htmltools/testData" + "/quickFix/createFileFromUsage";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightQuickFixFixture> quickFixFixture =
    lightQuickFixFixture(projectFixture, TEST_DATA_PATH, () -> List.of(new HtmlUnknownTargetInspection()));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    quickFixFixture.get().doTest(fileName);
  }
}
