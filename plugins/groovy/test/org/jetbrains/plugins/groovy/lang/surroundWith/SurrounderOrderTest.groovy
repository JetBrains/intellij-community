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
package org.jetbrains.plugins.groovy.lang.surroundWith

import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.openapi.actionSystem.Separator

/**
 * @author peter
 */
class SurrounderOrderTest extends LightCodeInsightFixtureTestCase {

  public void testStatementSurrounders() {
    myFixture.configureByText("a.groovy", "<selection>println a</selection>")

    def actions = SurroundWithHandler.buildSurroundActions(project, myFixture.editor, myFixture.file, null)
    def names = []
    for (action in actions) {
      if (action instanceof Separator) {
        break
      }

      def text = action.templatePresentation.text
      names << text.substring(text.indexOf('. ') + 2)
    }
    assertOrderedEquals names,
                        "if", "if / else", "while",
                        "{ -> ... }.call()",
                        "for", "try / catch", "try / finally", "try / catch / finally",
                        "shouldFail () {...}",
                        "(expr)", "((Type) expr)", "with (expr)",
                        "with () {...}"
  }

}