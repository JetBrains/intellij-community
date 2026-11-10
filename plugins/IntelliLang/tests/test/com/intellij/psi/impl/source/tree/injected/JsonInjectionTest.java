// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.json.JsonBundle;
import com.intellij.json.codeinsight.JsonStandardComplianceInspection;
import com.intellij.openapi.application.PathManager;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Checks that the JSON compliance inspection accepts a JSON fragment injected into a Java string.
 */
@TestApplication
public class JsonInjectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(PathManager.getCommunityHomePath() + "/plugins/IntelliLang/tests/testData/injected/");
  }


  // PY-133425
  @Test
  public void testJsonFragmentInjectedInJavaString() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new JsonStandardComplianceInspection());
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    List<HighlightInfo> highlightInfos = myFixture.doHighlighting();
    String message = JsonBundle.message("inspection.compliance.msg.illegal.property.key");
    assertNull(ContainerUtil.find(highlightInfos, info -> message.equals(info.getDescription())));
  }
}
