// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.surroundWith;

import com.intellij.codeInsight.generation.surroundWith.SurroundWithHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.application.WriteAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

@TestApplication
public class SurrounderOrderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testStatementSurrounders() {
    List<String> names = getSurrounders("<selection>println a</selection>");
    assertIterableEquals(List.of("if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally", "try / catch / finally", "shouldFail () {...}", "(expr)", "!(expr)", "((Type) expr)", "with () {...}", "<editor-fold...> Comments", "region...endregion Comments"), names);
  }

  @Test
  public void testStatementWithSemicolon() {
    List<String> names = getSurrounders("<selection>println a;</selection>");
    assertIterableEquals(List.of("if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally", "try / catch / finally", "shouldFail () {...}", "with () {...}", "<editor-fold...> Comments", "region...endregion Comments"), names);
  }

  @Test
  public void testStatementsWithComments() {
    List<String> names = getSurrounders("""
                                          <selection>println a; //a is very important
                                          println b
                                          println c /*also important */
                                          </selection>""");
    assertIterableEquals(List.of("if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally", "try / catch / finally", "shouldFail () {...}", "with () {...}", "<editor-fold...> Comments", "region...endregion Comments"), names);
  }

  @Test
  public void testInnerExpressionSurrounders() {
    List<String> names = getSurrounders("boolean a; println <selection>a</selection>");
    assertIterableEquals(List.of("(expr)", "!(expr)", "((Type) expr)"), names);
  }

  @Test
  public void testOuterExpressionSurrounders() {
    List<String> names = getSurrounders("boolean a; <selection>a</selection>");
    assertIterableEquals(List.of("if", "if / else", "while", "{ -> ... }.call()", "{}", "for", "try / catch", "try / finally", "try / catch / finally", "shouldFail () {...}", "(expr)", "!(expr)", "((Type) expr)", "with () {...}", "if (expr)", "if (expr) / else", "while (expr)", "with (expr)"), names);
  }

  private List<String> getSurrounders(final String fileText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
