/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.parser

import com.intellij.psi.PsiFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.groovy.util.TestUtils

abstract class GroovyParsingTestCase extends LightJavaCodeInsightFixtureTestCase {

  String getBasePath() {
    TestUtils.testDataPath + "parsing/groovy/"
  }

  void doTest() {
    doTest(getTestName(true).replace('$', '/') + ".test")
  }

  protected void doTest(String fileName) {
    String path = testDataPath + "/" + fileName
    def (String input) = TestUtils.readInput(path)
    checkParsing(input, fileName)
  }

  protected void checkParsing(String input, String path) {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(project, input)
    final String psiTree = DebugUtil.psiToString(psiFile, true)
    final String prefix = input + '\n-----\n'
    myFixture.configureByText('test.txt', prefix + psiTree.trim())
    myFixture.checkResultByFile(path, false)
  }

  protected checkParsingByText(String input, String output) {
    final PsiFile psiFile = TestUtils.createPseudoPhysicalGroovyFile(project, input)
    final String psiTree = DebugUtil.psiToString(psiFile, true)
    final String prefix = input.trim() + '\n-----\n'
    assertEquals(prefix + output.trim(), prefix + psiTree.trim())
  }
}
