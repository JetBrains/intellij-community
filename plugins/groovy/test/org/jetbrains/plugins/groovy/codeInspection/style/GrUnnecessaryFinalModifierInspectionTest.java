// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.style;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public final class GrUnnecessaryFinalModifierInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testRecord() {
    doTest("<warning descr=\"Modifier 'final' is not necessary\">final</warning> record Point(int x, int y) {}",
           "record Point(int x, int y) {}");
  }

  @Test
  public void testVal() {
    doTest("<warning descr=\"Modifier 'final' is not necessary\">final</warning> val x = 1",
           "var x = 1");
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new GrUnnecessaryFinalModifierInspection());
    myFixture.configureByText("_.groovy", before);
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Remove 'final' modifier"));
    myFixture.checkResult(after);
  }
}
