// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.fixes;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.GroovyProjectDescriptors.GROOVY_LATEST_REAL_JDK;
import static org.jetbrains.plugins.groovy.lang.highlighting.GrHighlightingFixtureKt.grHighlightingFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class GrCreateConstantFromJavaUsageTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GROOVY_LATEST_REAL_JDK);
  private final TestFixture<GrHighlightingFixture> highlighting = grHighlightingFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    highlighting.get().getFixture().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "fixes/createConstantFromJava/" + highlighting.get().getFixture().getTestName(true) + "/");
  }

  private void doTest(String action, int actionsCount) {
    LightJavaCodeInsightTestFixture myFixture = highlighting.get().getFixture();
    final String beforeGroovy = "Before.groovy";
    final String afterGroovy = "After.groovy";
    final String javaClass = "Area.java";

    myFixture.configureByFiles(javaClass, beforeGroovy);

    myFixture.enableInspections(highlighting.get().getCustomInspections());
    List<IntentionAction> fixes = myFixture.filterAvailableIntentions(action);
    assertEquals(fixes.size(), actionsCount);
    if (actionsCount == 0) return;

    myFixture.launchAction(fixes.get(0));
    PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
    myFixture.checkResultByFile(beforeGroovy, afterGroovy, true);
  }

  private void doTest() {
    doTest("Create constant", 1);
  }

  @Test
  public void testInterface() {
    doTest();
  }

  @Test
  public void testConstantInitializer() {
    doTest();
  }

  @Test
  public void testUppercaseInSuperInterface() {
    doTest("Create constant field 'BAR' in 'I'", 0);
  }
}
