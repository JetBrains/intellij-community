package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import ai.grazie.rules.en.EnglishParameters;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.utils.TextStyleDomain;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class AutoFixTest extends BaseTestCase {
  @BeforeEach
  public void setUp() {
    ((CodeInsightTestFixtureImpl) myFixture).canChangeDocumentDuringHighlighting(true);
    GrazieConfig.Companion.update(s -> s.withAutoFix(true));
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.RUSSIAN, Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());
    //noinspection unchecked
    myFixture.enableInspections(GrazieInspection.class, GrazieInspection.Grammar.class, GrazieInspection.Style.class);
  }

  @NeedsCloud
  @Test
  public void testRussianHyphenToDash() {
    myFixture.configureByText("a.md", "");
    typeAndCheck("Это - енот! А чего добился ты?", "Это — енот! А чего добился ты?");
  }

  private void typeAndCheck(String s, String expectedText) {
    myFixture.type(s);
    myFixture.doHighlighting();
    myFixture.checkResult(expectedText);
  }

  @NeedsCloud
  @Test
  public void testEnglishHyphenToEmDash() {
    myFixture.configureByText("a.md", "");
    typeAndCheck("I like spring - that's when the sun shines.", "I like spring – that's when the sun shines.");
  }

  @NeedsCloud
  @Test
  public void testEnglishHyphenToEnDash() {
    GrazieConfig.Companion.update(s -> s.withParameter(TextStyleDomain.Other, Language.ENGLISH, EnglishParameters.DASH_STYLE, "enDash"));
    myFixture.configureByText("a.md", "");
    typeAndCheck("I like spring - that's when the sun shines.", "I like spring – that's when the sun shines.");
  }

  @Test
  public void testNoFixesWithoutChanges() {
    String text = "Это - енот! А чего добился ты?";
    myFixture.configureByText("a.md", text);
    myFixture.doHighlighting();
    myFixture.checkResult(text);
  }

  @NeedsCloud
  @Test
  public void testNoAutoFixAfterUndo() {
    String dashText = "Это мы — молодцы!";
    String hyphenText = "Это мы - молодцы!";

    myFixture.configureByText("a.md", "Это мы <caret> молодцы!");
    myFixture.type("-");
    leaveTheHighlightedRegionForTheAutoFixToWork();
    myFixture.doHighlighting();
    myFixture.checkResult(dashText);

    ApplicationManager.getApplication().invokeAndWait(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        UndoManager.getInstance(getProject()).undo(FileEditorManager.getInstance(getProject()).getSelectedEditor());
      });
    });
    leaveTheHighlightedRegionForTheAutoFixToWork();

    myFixture.checkResult(hyphenText);
    myFixture.doHighlighting();
    myFixture.checkResult(hyphenText);
  }

  private void leaveTheHighlightedRegionForTheAutoFixToWork() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END);
  }

  @Test
  public void testNoHyphenToNoSpacedDashInTxt() {
    myFixture.configureByText("a.txt", "");
    String text = "I like spring - that's when the sun shines.";
    typeAndCheck(text, text);
  }

  @Test
  public void testNoDoubleHyphenToNoSpacedDashInTxt() {
    myFixture.configureByText("a.txt", "");
    String text = "I like spring - that's when the sun shines.";
    typeAndCheck(text, text);
  }

  @NeedsCloud
  @Test
  public void testGermanUmlautSubstitution() {
    myFixture.configureByText("a.txt", "");
    typeAndCheck("Ich, als dein immer bester Freund, wuensche dir beste Gruess", "Ich, als dein immer bester Freund, wünsche dir beste Gruess");
    // don't substitute ss yet: the caret is still inside the word
    typeAndCheck("e und du kannst alles machen was du moechtest", "Ich, als dein immer bester Freund, wünsche dir beste Grüße und du kannst alles machen was du moechtest");
    typeAndCheck(".", "Ich, als dein immer bester Freund, wünsche dir beste Grüße und du kannst alles machen was du möchtest.");
  }
}
