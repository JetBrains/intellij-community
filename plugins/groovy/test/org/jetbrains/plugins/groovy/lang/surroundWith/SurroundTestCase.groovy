/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.surroundWith
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.plugins.groovy.LightGroovyTestCase
import org.jetbrains.plugins.groovy.util.TestUtils
/**
 * @author peter
 */
public abstract class SurroundTestCase extends LightGroovyTestCase {
  protected void doTest(final Surrounder surrounder) {
    def (String before, String after) = TestUtils.readInput(testDataPath + "/" + getTestName(true) + ".test")
    doTest(surrounder, before, after)
  }

  protected void doTest(final Surrounder surrounder, String textBefore, String textAfter) {
    myFixture.configureByText("a.groovy", textBefore)

    WriteCommandAction.runWriteCommandAction project, {
      SurroundWithHandler.invoke(project, myFixture.editor, myFixture.file, surrounder)
      doPostponedFormatting(project)
    }

    myFixture.checkResult(textAfter)
  }
}
