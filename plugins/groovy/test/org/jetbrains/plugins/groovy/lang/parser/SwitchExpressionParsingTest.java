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
public class SwitchExpressionParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "statements/switch/expression");

  @Test
  public void testAsInCompiler1() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler2() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler3() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler4() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler5() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler6() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler7() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler8() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler9() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler10() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler11() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler12() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler13() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler14() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler15() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler16() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler17() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler18() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler19() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler20() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler21() { parsing.get().doTest(); }

  @Test
  public void testAsInCompiler22() { parsing.get().doTest(); }

  @Test
  public void testAsInCompilerFail1() { parsing.get().doTest(); }

  @Test
  public void testAsInCompilerFail2() { parsing.get().doTest(); }

  @Test
  public void testAsInCompilerFail3() { parsing.get().doTest(); }

  @Test
  public void testCustom1() { parsing.get().doTest(); }

  @Test
  public void testCustom2() { parsing.get().doTest(); }

  @Test
  public void testCustom3() { parsing.get().doTest(); }

  @Test
  public void testCustom4() { parsing.get().doTest(); }

  @Test
  public void testCustom5() { parsing.get().doTest(); }

  @Test
  public void testCustom6() { parsing.get().doTest(); }

  @Test
  public void testCustom7() { parsing.get().doTest(); }
}
