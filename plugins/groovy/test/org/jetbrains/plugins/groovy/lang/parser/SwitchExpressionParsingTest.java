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
public class SwitchExpressionParsingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyParsingFixture> parsing =
    groovyParsingFixture(projectFixture, TestUtils.getAbsoluteTestDataPath() + "parsing/groovy/" + "statements/switch/expression");

  private void doTest() {
    parsing.get().doTest();
  }

  @Test public void testAsInCompiler1() { doTest(); }
  @Test public void testAsInCompiler2() { doTest(); }
  @Test public void testAsInCompiler3() { doTest(); }
  @Test public void testAsInCompiler4() { doTest(); }
  @Test public void testAsInCompiler5() { doTest(); }
  @Test public void testAsInCompiler6() { doTest(); }
  @Test public void testAsInCompiler7() { doTest(); }
  @Test public void testAsInCompiler8() { doTest(); }
  @Test public void testAsInCompiler9() { doTest(); }
  @Test public void testAsInCompiler10() { doTest(); }
  @Test public void testAsInCompiler11() { doTest(); }
  @Test public void testAsInCompiler12() { doTest(); }
  @Test public void testAsInCompiler13() { doTest(); }
  @Test public void testAsInCompiler14() { doTest(); }
  @Test public void testAsInCompiler15() { doTest(); }
  @Test public void testAsInCompiler16() { doTest(); }
  @Test public void testAsInCompiler17() { doTest(); }
  @Test public void testAsInCompiler18() { doTest(); }
  @Test public void testAsInCompiler19() { doTest(); }
  @Test public void testAsInCompiler20() { doTest(); }
  @Test public void testAsInCompiler21() { doTest(); }
  @Test public void testAsInCompiler22() { doTest(); }
  @Test public void testAsInCompilerFail1() { doTest(); }
  @Test public void testAsInCompilerFail2() { doTest(); }
  @Test public void testAsInCompilerFail3() { doTest(); }
  @Test public void testCustom1() { doTest(); }
  @Test public void testCustom2() { doTest(); }
  @Test public void testCustom3() { doTest(); }
  @Test public void testCustom4() { doTest(); }
  @Test public void testCustom5() { doTest(); }
  @Test public void testCustom6() { doTest(); }
  @Test public void testCustom7() { doTest(); }
}
