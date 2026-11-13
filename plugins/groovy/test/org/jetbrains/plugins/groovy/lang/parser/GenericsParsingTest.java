// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.parser.GroovyParsingFixtureKt.groovyParsingFixture;

@TestApplication
public class GenericsParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "generics");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testErr1() { doTest(); }
  @Test public void testErr2() { doTest(); }
  @Test public void testErr3() { doTest(); }
  @Test public void testErr4() { doTest(); }
  @Test public void testGenmethod1() { doTest(); }
  @Test public void testGenmethod2() { doTest(); }
  @Test public void testGenmethod3() { doTest(); }
  @Test public void testGenmethod4() { doTest(); }
  @Test public void testGenmethod5() { doTest(); }
  @Test public void testTypeargs1() { doTest(); }
  @Test public void testTypeparam1() { doTest(); }
  @Test public void testTypeparam2() { doTest(); }
  @Test public void testTypeparam3() { doTest(); }
  @Test public void testNewLineAfterAngle() { doTest(); }
  @Test public void testNewLineAfterComma() { doTest(); }
  @Test public void testNewLineBeforeAngle() { doTest(); }
  @Test public void testNewLineBeforeComma() { doTest(); }
  @Test public void testNewLineBeforeExtendsInTypeParameterBounds() { doTest(); }
  @Test public void testNewLineAfterExtendsInTypeParameterBounds() { doTest(); }
  @Test public void testNewLineBeforeAmpInTypeParameterBounds() { doTest(); }
  @Test public void testNewLineAfterAmpInTypeParameterBounds() { doTest(); }
  @Test public void testTopLevelMethodWithoutModifiers() { doTest(); }
  @Test public void testClassLevelMethodWithoutModifiers() { doTest(); }
  @Test public void testClassLevelMethodWithoutModifiers2() { doTest(); }
}
