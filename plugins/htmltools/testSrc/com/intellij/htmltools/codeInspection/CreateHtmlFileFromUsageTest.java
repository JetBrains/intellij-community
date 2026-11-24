package com.intellij.htmltools.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.HtmlUnknownTargetInspection;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFileBasedTest;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class CreateHtmlFileFromUsageTest extends LightQuickFixFileBasedTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return codeInsightFixture.get();
  }

  @Override
  public @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/plugins/htmltools/testData" + "/quickFix/createFileFromUsage";
  }

  @BeforeEach
  void setUpInspections() {
    getFixture().enableInspections(new HtmlUnknownTargetInspection());
  }
}
