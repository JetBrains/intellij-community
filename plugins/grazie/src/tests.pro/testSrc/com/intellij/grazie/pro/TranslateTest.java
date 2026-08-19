package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import com.intellij.grazie.GrazieBundle;
import com.intellij.grazie.cloud.APIQueries;
import com.intellij.grazie.ide.inspection.ai.TranslateAction;
import com.intellij.openapi.ui.TestDialog;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("NonAsciiCharacters")
@NeedsCloud
public class TranslateTest extends BaseTestCase {
  private static String getIntentionText() {
    return GrazieBundle.message("intention.translate.text");
  }

  private void mockTranslation() {
    Map<String, String> translations = Map.of(
      "A library is a collection of compiled code that you can add to your project.",
      "Eine Bibliothek ist eine Sammlung kompilierter Code, die Sie an Ihr Projekt hinzufügen können.",

      "Another line.", "Eine weitere Zeile.",
      "Something {0} unknown", "Etwas {0} unbekanntes",
      "receive", "bekommen",

      "This is the first sentence. This is the second sentence.",
      "Das ist der erste Satz. Das ist der zweite Satz."
    );

    APIQueries.overrideTranslator((texts, _, _) -> ContainerUtil.map(texts, s -> {
        String t = translations.get(s);
        if (t == null) throw new IllegalArgumentException("No test translation for: " + s);
        return t;
      }), getTestRootDisposable());
  }

  private static void translateIntoGerman() {
    UiInterceptors.register(
      new ChooserInterceptor(
        StreamEx.of(Language.getEntries())
          .filter(l -> l != Language.ENGLISH && l != Language.UNKNOWN)
          .map(it -> TranslateAction.presentableLanguageName(it))
          .toList(),
        "Deutsch"
      ));
  }

  @BeforeEach
  protected void setUp() {
    GrazieTestUtil.registerGrazieCloudConnectorWithQuota(getTestRootDisposable());
  }

  @AfterEach
  protected void tearDown() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT);
  }

  @Test
  public void testFragmentUnderCaret() {
    mockTranslation();
    translateIntoGerman();
    checkIntention("a.md", getIntentionText(),
      "<caret>A library is a collection of compiled code that you can add to your project.\n\nAnother sentence.",
      "Eine Bibliothek ist eine Sammlung kompilierter Code, die Sie an Ihr Projekt hinzufügen können.\n\nAnother sentence.");
  }

  @Test
  public void testSelection() {
    mockTranslation();
    translateIntoGerman();
    checkIntention("a.md", getIntentionText(),
      "Before. <caret><selection>A library is a collection of compiled code that you can add to your project.\n\nAnother line.</selection> After.",
      "Before. Eine Bibliothek ist eine Sammlung kompilierter Code, die Sie an Ihr Projekt hinzufügen können.\n\nEine weitere Zeile. After.");
  }

  @Test
  public void testSingleEnglishWord() {
    mockTranslation();
    UiInterceptors.register(
      new ChooserInterceptor(
        StreamEx.of(Language.getEntries())
          .filter(l -> l != Language.UNKNOWN)
          .map(it -> TranslateAction.presentableLanguageName(it))
          .toList(),
        "Deutsch"
      ));
    checkIntention("a.md", getIntentionText(), "<selection>receive</selection>", "bekommen");
  }

  @Test
  public void testSingleLineSelectionInMultiLineComment() {
    mockTranslation();
    translateIntoGerman();
    checkIntention("a.java", getIntentionText(),
      """
        /**
          * <selection><caret>This is the first sentence. This is the second sentence.</selection>
          * And this is the third sentence. And another sentence.
          */
        """,
      """
        /**
          * Das ist der erste Satz. Das ist der zweite Satz.
          * And this is the third sentence. And another sentence.
          */
        """);
  }

  @Test
  public void testProcessFragmentsWithInjections() {
    mockTranslation();
    translateIntoGerman();
    checkIntention("a.properties", getIntentionText(),
      "<selection>prop1=A library is a collection of compiled code that you can add to your project.\n" +
      "prop2=Something {0} unknown</selection>",
      "<selection>prop1=Eine Bibliothek ist eine Sammlung kompilierter Code, die Sie an Ihr Projekt hinzufügen können.\n" +
      "prop2=Etwas {0} unbekanntes</selection>");
  }

  @Test
  public void testRealServerTranslation() {
    translateIntoGerman();
    myFixture.configureByText("a.txt", "<selection>I see this.</selection>");
    myFixture.launchAction(findSingleIntention(getIntentionText()));
    String result = myFixture.getEditor().getDocument().getText();
    assertTrue(result.toLowerCase(Locale.ROOT).contains("ich"), result);
  }

  @Test
  public void testNoTranslationForNonNaturalText() {
    checkIntentionIsAbsent("test.yaml", getIntentionText(), """
      rec<caret>eive:
        id: 123
        topic: K
        message: Hello World
      """);
  }
}
