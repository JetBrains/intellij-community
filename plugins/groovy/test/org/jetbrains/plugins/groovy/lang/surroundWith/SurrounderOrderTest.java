// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

public class SurrounderOrderTest extends LightJavaCodeInsightFixtureTestCase {
  public void testStatementSurrounders() {
    List<String> names = getSurrounders("<selection>println a</selection>");
    UsefulTestCase.assertOrderedEquals(names, "if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally",
                                       "try / catch / finally", "shouldFail () {...}", "(expr)", "!(expr)", "((Type) expr)",
                                       "with () {...}", "<editor-fold...> Comments", "region...endregion Comments");
  }

  public void testStatementWithSemicolon() {
    List<String> names = getSurrounders("<selection>println a;</selection>");
    UsefulTestCase.assertOrderedEquals(names, "if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally",
                                       "try / catch / finally", "shouldFail () {...}", "with () {...}", "<editor-fold...> Comments",
                                       "region...endregion Comments");
  }

  public void testStatementsWithComments() {
    List<String> names = getSurrounders("""
                                          <selection>println a; //a is very important
                                          println b
                                          println c /*also important */
                                          </selection>""");
    UsefulTestCase.assertOrderedEquals(names, "if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally",
                                       "try / catch / finally", "shouldFail () {...}", "with () {...}", "<editor-fold...> Comments",
                                       "region...endregion Comments");
  }

  public void testInnerExpressionSurrounders() {
    List<String> names = getSurrounders("boolean a; println <selection>a</selection>");
    UsefulTestCase.assertOrderedEquals(names, "(expr)", "!(expr)", "((Type) expr)");
  }

  public void testOuterExpressionSurrounders() {
    List<String> names = getSurrounders("boolean a; <selection>a</selection>");
    UsefulTestCase.assertOrderedEquals(names, "if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally",
                                       "try / catch / finally", "shouldFail () {...}", "(expr)", "!(expr)", "((Type) expr)",
                                       "with () {...}", "if (expr)", "if (expr) / else", "while (expr)", "with (expr)");
  }

  private List<String> getSurrounders(final String fileText) {
    myFixture.configureByText("a.groovy", fileText);

    return WriteAction.compute(() -> {
      var actions = SurroundWithHandler.buildSurroundActions(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      List<String> names = new ArrayList<>();
      for (AnAction action : actions) {
        if (action instanceof Separator) break;
        String text = action.getTemplatePresentation().getText();
        names.add(text.substring(text.indexOf(". ") + 2));
      }
      return names;
    });
  }
}
