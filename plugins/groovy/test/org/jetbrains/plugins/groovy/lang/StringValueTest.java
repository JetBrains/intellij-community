// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.util.TestUtils;

/**
 * @author Max Medvedev
 */
public class StringValueTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "stringValues/";
  }

  private void doTest(@Nullable String expected) {
    GroovyFile file = (GroovyFile)myFixture.configureByFile(getTestName(false) + ".groovy");
    GrLiteral literal = (GrLiteral)file.getStatements()[0];
    TestCase.assertEquals(expected, literal.getValue());
  }

  public void testString0() {
    doTest("fo\nabc \r ሴ \" ' \" ");
  }

  public void testString1() {
    doTest(null);
  }

  public void testString2() {
    doTest(null);
  }

  public void testString3() {
    doTest("fooabc");
  }

  public void testGString() {
    doTest("abc ' \" ");
  }

  public void testSlashString() {
    doTest("abc \\a \\n /  abc ሴ $ \\$ ");
  }

  public void _testDollarSlashString() {
    doTest("/abc \\n \\o ሴ  abc $ / \\$ /");
  }
}
