// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.openapi.application.ReadAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Max Medvedev
 */
@TestApplication
public class StringValueTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "stringValues/");
  }

  private void doTest(@Nullable String expected) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    GroovyFile file = (GroovyFile)myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");
    ReadAction.runBlocking(() -> {
      GrLiteral literal = (GrLiteral)file.getStatements()[0];
      assertEquals(expected, literal.getValue());
    });
  }

  @Test
  public void testString0() {
    doTest("fo\nabc \r ሴ \" ' \" ");
  }

  @Test
  public void testString1() {
    doTest(null);
  }

  @Test
  public void testString2() {
    doTest(null);
  }

  @Test
  public void testString3() {
    doTest("fooabc");
  }

  @Test
  public void testGString() {
    doTest("abc ' \" ");
  }

  @Test
  public void testSlashString() {
    doTest("abc \\a \\n /  abc ሴ $ \\$ ");
  }

  public void _testDollarSlashString() {
    doTest("/abc \\n \\o ሴ  abc $ / \\$ /");
  }
}
