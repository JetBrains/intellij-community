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
package org.jetbrains.plugins.groovy.refactoring.convertToJava

import com.intellij.psi.PsiFile
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * @author Maxim.Medvedev
 */
class FileGenerationTest extends LightGroovyTestCase {
  @Override
  protected String getBasePath() {
    return "${TestUtils.testDataPath}refactoring/convertGroovyToJava/file";
  }

  private void doTest() {
    final String testName = getTestName(true)
    final PsiFile file = myFixture.configureByFile("${testName}.groovy");
    assertInstanceOf file, GroovyFile

    new ConvertToJavaProcessor(project, [file] as GroovyFile[]).run()

    myFixture.checkResultByFile("${testName}.java")
  }

  void testEnum() {doTest()}

  void testGrScript() {doTest()}

  void testConstructor() {doTest()}

  void testReturns() {doTest()}
  void testReturn2() {doTest()}
}
