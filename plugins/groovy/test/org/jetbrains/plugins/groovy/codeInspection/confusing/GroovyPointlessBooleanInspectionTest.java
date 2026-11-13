// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInspection.confusing;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.util.ActionTest;
import org.jetbrains.plugins.groovy.util.HighlightingTest;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class GroovyPointlessBooleanInspectionTest implements HighlightingTest, ActionTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = groovyFixture(project);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  @Override
  public @NotNull Collection<Class<? extends LocalInspectionTool>> getInspections() {
    return List.of(GroovyPointlessBooleanInspection.class);
  }

  @Test
  public void simpleUnary() {
    runInEdtAndWait(() -> {
      doTest("<warning descr=\"Redundant boolean operations\">!false</warning>", "true");
      doTest("<warning descr=\"Redundant boolean operations\">!true</warning>", "false");
    });
  }

  @Test
  public void doubleNegationUnary() {
    runInEdtAndWait(() -> {
      doTest("<warning descr=\"Redundant boolean operations\">!!false</warning>", "false");
      doTest("<warning descr=\"Redundant boolean operations\">!!true</warning>", "true");
    });
  }

  @Test
  public void tripleNegationUnary() {
    runInEdtAndWait(() -> {
      doTest("!<caret><warning descr=\"Redundant boolean operations\">!!false</warning>", "!false");
      doTest( "!<caret><warning descr=\"Redundant boolean operations\">!!true</warning>", "!true");
    });
  }

  @Test
  public void implicationExpression() {
    runInEdtAndWait(() -> {
      doTest("<warning descr=\"Redundant boolean operations\">false ==> (false)</warning>", "(false)");
      doTest( "<warning descr=\"Redundant boolean operations\">(false) ==> true</warning>", "(false)");
    });
  }

  @Test
  public void nonPrimitiveType() {
    runInEdtAndWait(() -> highlightingTest("Boolean x = null; x == true"));
  }

  private void doTest(String before, String after) {
    highlightingTest(before);
    doActionTest("Simplify", after);
  }
}
