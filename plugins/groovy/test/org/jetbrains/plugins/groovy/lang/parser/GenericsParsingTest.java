// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Test
  public void testErr1() { parsing.get().doTest(); }

  @Test
  public void testErr2() { parsing.get().doTest(); }

  @Test
  public void testErr3() { parsing.get().doTest(); }

  @Test
  public void testErr4() { parsing.get().doTest(); }

  @Test
  public void testGenmethod1() { parsing.get().doTest(); }

  @Test
  public void testGenmethod2() { parsing.get().doTest(); }

  @Test
  public void testGenmethod3() { parsing.get().doTest(); }

  @Test
  public void testGenmethod4() { parsing.get().doTest(); }

  @Test
  public void testGenmethod5() { parsing.get().doTest(); }

  @Test
  public void testTypeargs1() { parsing.get().doTest(); }

  @Test
  public void testTypeparam1() { parsing.get().doTest(); }

  @Test
  public void testTypeparam2() { parsing.get().doTest(); }

  @Test
  public void testTypeparam3() { parsing.get().doTest(); }

  @Test
  public void testNewLineAfterAngle() { parsing.get().doTest(); }

  @Test
  public void testNewLineAfterComma() { parsing.get().doTest(); }

  @Test
  public void testNewLineBeforeAngle() { parsing.get().doTest(); }

  @Test
  public void testNewLineBeforeComma() { parsing.get().doTest(); }

  @Test
  public void testNewLineBeforeExtendsInTypeParameterBounds() { parsing.get().doTest(); }

  @Test
  public void testNewLineAfterExtendsInTypeParameterBounds() { parsing.get().doTest(); }

  @Test
  public void testNewLineBeforeAmpInTypeParameterBounds() { parsing.get().doTest(); }

  @Test
  public void testNewLineAfterAmpInTypeParameterBounds() { parsing.get().doTest(); }

  @Test
  public void testTopLevelMethodWithoutModifiers() { parsing.get().doTest(); }

  @Test
  public void testClassLevelMethodWithoutModifiers() { parsing.get().doTest(); }

  @Test
  public void testClassLevelMethodWithoutModifiers2() { parsing.get().doTest(); }
}
