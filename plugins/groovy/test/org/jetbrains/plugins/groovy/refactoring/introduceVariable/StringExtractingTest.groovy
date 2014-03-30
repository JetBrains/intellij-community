/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.introduceVariable

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.refactoring.introduce.StringPartInfo
import org.jetbrains.plugins.groovy.util.TestUtils

/**
 * Created by Max Medvedev on 04/02/14
 */
class StringExtractingTest extends LightGroovyTestCase {

  @Override
  protected String getBasePath() {
    return TestUtils.testDataPath + "refactoring/stringExtracting/"
  }

  public void testStringExtractingFromQuote() { doTest() }

  public void testStringExtractingFromDoubleQuotes() { doTest() }

  public void testStringExtractingFromSlashyString() { doTest() }

  public void testStringExtractingFromDollarSlashyString() { doTest() }

  public void testSlashyWithSlash() { doTest() }

  public void testDollarSlashyWithDollar() { doTest() }

  public void testSlashyWithSlashInsideExtractedPart() { doTest() }

  private void doTest() {
    GroovyFile file = myFixture.configureByFile(getTestName(true) + '.groovy') as GroovyFile

    TextRange range = new TextRange(*myFixture.editor.selectionModel.with { [selectionStart, selectionEnd] })

    CommandProcessor.instance.executeCommand(myFixture.project, {
      ApplicationManager.application.runWriteAction {
        new StringPartInfo(PsiUtil.skipParentheses(file.statements[0], false) as GrLiteral, range).replaceLiteralWithConcatenation(null)
        myFixture.editor.selectionModel.removeSelection()
      }
    }, null, null)

    myFixture.checkResultByFile(getTestName(true) + '_after.groovy')
  }
}
