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
package org.jetbrains.plugins.groovy.lang.surroundWith

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.WriteAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class SurrounderOrderTest extends LightJavaCodeInsightFixtureTestCase {

  void testStatementSurrounders() {
    def names = getSurrounders("<selection>println a</selection>")
    assertOrderedEquals names,
                        "if", "if / else",
                        "while",
                        "{ -> ... }.call()",
                        "{}",
                        "for", "try / catch",
                        "try / finally",
                        "try / catch / finally",
                        "shouldFail () {...}",
                        "(expr)",
                        "!(expr)",
                        "((Type) expr)",
                        "with () {...}",
                        "<editor-fold...> Comments",
                        "region...endregion Comments"
  }

  void testStatementWithSemicolon() throws Exception {
    def names = getSurrounders("<selection>println a;</selection>")
    assertOrderedEquals names,
                        "if", "if / else", "while",
                        "{ -> ... }.call()",
                        "{}",
                        "for", "try / catch", "try / finally", "try / catch / finally",
                        "shouldFail () {...}",
                        "with () {...}",
                        "<editor-fold...> Comments",
                        "region...endregion Comments"
  }

  void testStatementsWithComments() throws Exception {
    def names = getSurrounders("""<selection>println a; //a is very important
println b
println c /*also important */
</selection>""")
    assertOrderedEquals names,
                        "if", "if / else", "while",
                        "{ -> ... }.call()",
                        "{}",
                        "for", "try / catch", "try / finally", "try / catch / finally",
                        "shouldFail () {...}",
                        "with () {...}",
                        "<editor-fold...> Comments",
                        "region...endregion Comments"
  }

  void testInnerExpressionSurrounders() {
    def names = getSurrounders("boolean a; println <selection>a</selection>")
    assertOrderedEquals names, "(expr)", "!(expr)", "((Type) expr)"
  }

  void testOuterExpressionSurrounders() {
    def names = getSurrounders("boolean a; <selection>a</selection>")
    assertOrderedEquals names,
                        "if",
                        "if / else",
                        "while",
                        "{ -> ... }.call()",
                        "{}",
                        "for",
                        "try / catch",
                        "try / finally",
                        "try / catch / finally",
                        "shouldFail () {...}",
                        "(expr)",
                        "!(expr)",
                        "((Type) expr)",
                        "with () {...}",
                        "if (expr)",
                        "if (expr) / else",
                        "while (expr)",
                        "with (expr)"
  }

  private List<String> getSurrounders(final String fileText) {
    myFixture.configureByText("a.groovy", fileText)

    WriteAction.compute {
      def actions = SurroundWithHandler.buildSurroundActions(project, myFixture.editor, myFixture.file)
      def names = []
      for (action in actions) {
        if (action instanceof Separator) {
          break
        }

        def text = action.templatePresentation.text
        names << text.substring(text.indexOf('. ') + 2)
      }
      return names
    }
  }
}