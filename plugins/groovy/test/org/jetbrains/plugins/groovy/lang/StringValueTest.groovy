/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.lang

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Max Medvedev
 */
class StringValueTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    "${TestUtils.testDataPath}stringValues/"
  }

  private void doTest(@Nullable String expected) {
    def file = myFixture.configureByFile("${getTestName(false)}.groovy") as GroovyFile
    def literal = file.statements[0] as GrLiteral
    assertEquals expected, literal.value
  }

  void testString0() {
    doTest("fo\nabc \r \u1234 \" \' \" ")
  }

  void testString1() {
    doTest(null)
  }

  void testString2() {
    doTest(null)
  }

  void testString3() {
    doTest("fooabc")
  }

  void testGString() {
    doTest("abc \' \" ")
  }

  void testSlashString() {
    doTest("abc \\a \\n /  abc \u1234 \$ \\\$ ")
  }

  void _testDollarSlashString() {
    doTest("/abc \\n \\o \u1234  abc \$ / \\\$ /")
  }
}
