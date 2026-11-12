// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.formatter;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.formatter.GroovyFormatterFixtureKt.groovyFormatterFixture;

/**
 * @author Max Medvedev
 */
@TestApplication
public class WrappingTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<GroovyFormatterFixture> formatter = groovyFormatterFixture(projectFixture);

  @BeforeEach
  void setUp() {
    formatter.get().getTempSettings().setRightMargin(GroovyLanguage.INSTANCE, 10);
  }

  @Test
  public void testWrapChainedMethodCalls() {
    formatter.get().getGroovySettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;

    formatter.get().checkFormatting("""
                      foo().barbar().abcd()
                      """, """
                      foo()
                          .barbar()
                          .abcd()
                      """);
  }

  @Test
  public void testWrapChainedMethodCallsWithDotAfter() {
    formatter.get().getGroovySettings().METHOD_CALL_CHAIN_WRAP = CommonCodeStyleSettings.WRAP_ALWAYS;
    formatter.get().getGroovyCustomSettings().WRAP_CHAIN_CALLS_AFTER_DOT = true;

    formatter.get().checkFormatting("""
                      foo().barbar().abcd()
                      """, """
                      foo().
                          barbar().
                          abcd()
                      """);
  }

  @Test
  public void testWrappingInsideGString0() {
    formatter.get().checkFormatting("""
                      "abcdefghij${a+b}"
                      """, """
                      "abcdefghij${a + b}"
                      """);
  }

  @Test
  public void testWrappingInsideGString1() {
    formatter.get().checkFormatting("""
                      ""\"abcdefghij${a+b}""\"
                      """, """
                      ""\"abcdefghij${a + b}""\"
                      """);
  }

  @Test
  public void testWrappingInsideGString2() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      ""\"abcdefghij${a+b}""\"
                      """, """
                      ""\"abcdefghij${
                        a + b
                      }""\"
                      """);
  }

  @Test
  public void testWrappingInsideGString3() {
    formatter.get().checkFormatting("""
                      ""\"text with ${foo}""\"
                      """, """
                      ""\"text with ${foo}""\"
                      """);
  }

  @Test
  public void testWrappingInsideGString4() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      ""\"text with ${foooo}""\"
                      """, """
                      ""\"text with ${
                        foooo
                      }""\"
                      """);
  }

  @Test
  public void testWrappingInsideGString5() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      ""\"text with ${foo}""\"
                      """, """
                      ""\"text with ${
                        foo}""\"
                      """);
  }

  @Test
  public void testWrapArgumentList() {
    formatter.get().getGroovySettings().CALL_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_AS_NEEDED;
    formatter.get().checkFormatting("""
                      printxxxxxx(2)
                      """, """
                      printxxxxxx(
                          2)
                      """);
  }

  @Test
  public void testWrapOneLineClosure() {
    formatter.get().checkFormatting("""
                      ""\"def barbar = {foo}""\"
                      """, """
                      ""\"def barbar = {foo}""\"
                      """);
  }

  @Test
  public void testWrapOneLineClosure2() {
    formatter.get().getGroovySettings().KEEP_SIMPLE_LAMBDAS_IN_ONE_LINE = false;
    formatter.get().checkFormatting("""
                      def barbarbar = {foo}
                      """, """
                      def barbarbar = {
                        foo
                      }
                      """);
  }
}
