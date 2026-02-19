package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import ai.grazie.rules.en.EnglishParameters;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.TextProblemSeverities;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.utils.TextStyleDomain;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.intellij.testFramework.UsefulTestCase.assertEmpty;
import static com.intellij.testFramework.UsefulTestCase.assertOneElement;
import static com.intellij.testFramework.UsefulTestCase.assertOrderedEquals;

@SuppressWarnings("NonAsciiCharacters")
public class TextLevelHighlightingTest extends BaseTestCase {

  @BeforeEach
  public void setUp() {
    myFixture.enableInspections(GrazieInspection.class, GrazieInspection.Grammar.class, GrazieInspection.Style.class);
  }

  @NeedsCloud
  @Test
  public void testSentenceCapitalization() {
    myFixture.configureByText("a.md", """
      # this header is an unaffected separate fragment
      
      <caret><STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.SENTENCE_CAPITALIZATION">th</STYLE_SUGGESTION>is is a lowercase sentence.
      And this is a capitalized sentence. <STYLE_SUGGESTION>an</STYLE_SUGGESTION>d another lowercase one
      """);
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Start all sentences with an uppercase letter"));
    myFixture.checkResult("""
      # this header is an unaffected separate fragment
      
      This is a lowercase sentence.
      And this is a capitalized sentence. And another lowercase one
      """);
  }

  @NeedsCloud
  @Test
  public void testGermanSentenceCapitalization() {
    HighlightingTest.enableLanguages(Set.of(Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());
    myFixture.configureByText("a.md", """
      Sehr geehrte Damen und Herren,
      
      ich schreibe Ihnen jetzt. <STYLE_SUGGESTION><caret>ich</STYLE_SUGGESTION> mag den Schreibprozess.
      """);
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("Ich"));
    myFixture.checkResult("""
      Sehr geehrte Damen und Herren,
      
      ich schreibe Ihnen jetzt. Ich mag den Schreibprozess.
      """);
  }

  @Test
  public void testAllowMixedCapitalizationInProperties() {
    myFixture.configureByText("a.properties", """
      p1=A capitalized sentence that's long enough to be detected as English
      p2=a non-capitalized sentence that's long enough to be detected as English
      """);
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testJavadocSentenceCapitalization() {
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());
    myFixture.configureByText("a.java", """
      /**
       * A capitalized sentence.
       *
       * <STYLE_SUGGESTION>a </STYLE_SUGGESTION>non-capitalized one in a long enough text
       * @see C a "see" non-capitalized noun phrase. Then a capitalized sentence, which is also OK.
       * @deprecated a "deprecated" non-capitalized noun phrase.
       *             Then a capitalized sentence <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Punctuation.RELATIVE_CLAUSE_COMMA">which</GRAMMAR_ERROR> is long enough to be detected.
       *             <caret><STYLE_SUGGESTION>th</STYLE_SUGGESTION>en another non-capitalized sentence.
       */
      class C {}

      /**
      * The first sentence.
      * {@link D} the second sentence.
      */
      class D {
          /**
           * <STYLE_SUGGESTION descr="Grazie.RuleEngine.De.Style.SENTENCE_CAPITALIZATION">ein</STYLE_SUGGESTION> Satz, der mit einem Großbuchstaben beginnen sollte.
           *
           * @param name der Name, der angezeigt werden sollte.
           */
          void foo(int name) {}
      }
      """);
    myFixture.checkHighlighting();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myFixture.launchAction("Start all sentences with an uppercase letter");
    });
    myFixture.checkResult("""
      /**
       * A capitalized sentence.
       *
       * a non-capitalized one in a long enough text
       * @see C a "see" non-capitalized noun phrase. Then a capitalized sentence, which is also OK.
       * @deprecated a "deprecated" non-capitalized noun phrase.
       *             Then a capitalized sentence which is long enough to be detected.
       *             Then another non-capitalized sentence.
       */
      class C {}

      /**
      * The first sentence.
      * {@link D} the second sentence.
      */
      class D {
          /**
           * ein Satz, der mit einem Großbuchstaben beginnen sollte.
           *
           * @param name der Name, der angezeigt werden sollte.
           */
          void foo(int name) {}
      }
      """);
  }

  @Test
  public void testNoConsistencyChecksInComments() {
    myFixture.configureByText("a.java", """
      // It was here, there and everywhere.
      // and I mean elephants, tigers, or lions in the wild.
      """);
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testKdocSentenceCapitalization() {
    myFixture.configureByText("a.kt", """
      /**
       * A capitalized sentence.
       *
       * <STYLE_SUGGESTION>a </STYLE_SUGGESTION>non-capitalized one in a long enough text
       * @see C a "see C" non-capitalized noun phrase. Then a capitalized sentence, which is also OK.
       */
      class C {}
      
      /**
       * @see D a "see D" non-capitalized noun phrase.
       *        Then a capitalized sentence <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Punctuation.RELATIVE_CLAUSE_COMMA">which</GRAMMAR_ERROR> is long enough to be detected.
       *        <STYLE_SUGGESTION>th</STYLE_SUGGESTION>en another non-capitalized sentence.
       */
      class D {}
      """);
    myFixture.checkHighlighting();
  }

  public void testDocumentWideConsistency() {
    GrazieConfig.Companion.update(it -> it.withParameter(TextStyleDomain.Other, Language.ENGLISH, EnglishParameters.CONTRACTION_USE, "consistently"));
    HighlightingTest.enableRules("Grazie.RuleEngine.En.Style.ENFORCE_CONTRACTION_USE");

    myFixture.configureByText("a.md", """
      # <STYLE_SUGGESTION descr="Use contracted forms consistently">I<caret>'ve</STYLE_SUGGESTION> been in many places last year.
      
      It was here, <STYLE_SUGGESTION descr="Inconsistent serial (Oxford) comma usage">there and</STYLE_SUGGESTION> everywhere
      
      But I <STYLE_SUGGESTION descr="Use contracted forms consistently">have not</STYLE_SUGGESTION> seen them.
      And I mean elephants, <STYLE_SUGGESTION descr="Inconsistent serial (Oxford) comma usage">tigers,</STYLE_SUGGESTION> or lions in the wild.
      """);
    myFixture.checkHighlighting();

    assertOrderedEquals(
      ContainerUtil.map(myFixture.getAvailableIntentions(), i -> i.getText()).subList(0, 4),
      "Use unambiguous contracted forms everywhere",
      "Use full forms everywhere",
      "Ignore 'I've' in this sentence",
      "Edit inspection profile setting"
    );
  }

  @Test
  @NeedsCloud
  public void testIgnoringSingleConsistencyDeviationistRemovesOtherWarnings() {
    myFixture.configureByText("a.md", """
      It was here, <STYLE_SUGGESTION>there and</STYLE_SUGGESTION> everywhere.
      And I mean elephants, <STYLE_SUGGESTION><caret>tigers,</STYLE_SUGGESTION> or lions in the wild.
      """);
    myFixture.checkHighlighting();

    List<IntentionAction> intentions = myFixture.getAvailableIntentions();
    assertOrderedEquals(
      ContainerUtil.map(intentions, i -> i.getText()).subList(0, 4),
      "Insert serial commas everywhere",
      "Remove serial commas everywhere",
      "Ignore 'tigers,' in this sentence",
      "Edit inspection profile setting"
    );

    myFixture.launchAction(intentions.get(2));
    assertEmpty(myFixture.doHighlighting(TextProblemSeverities.STYLE_SUGGESTION));
  }

  @NeedsCloud
  @Test
  public void testIgnoringCapitalizationWarningRemovesItsReplacement() {
    //todo remove the hyphens and quotes when https://youtrack.jetbrains.com/issue/IJPL-186501/Wrong-context-sentence-is-added-to-exceptions-when-ignoring-an-ML-grammar-warning is fixed
    myFixture.configureByText("a.md", """
      The first sentence.
      <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Punctuation.FORMATTING_ISSUES">" -</GRAMMAR_ERROR> <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.SENTENCE_CAPITALIZATION"><caret>th</STYLE_SUGGESTION>e second sentence."
      <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.SENTENCE_CAPITALIZATION">th</STYLE_SUGGESTION>e third sentence.
      """);
    myFixture.checkHighlighting();

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myFixture.launchAction("Ignore 'th' in this sentence");
    });
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_START);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN);
    assertOneElement(myFixture.doHighlighting().stream().filter(i -> i.getSeverity() == TextProblemSeverities.STYLE_SUGGESTION).toList());

    ApplicationManager.getApplication().invokeAndWait(() -> {
      myFixture.launchAction("Start all sentences with an uppercase letter");
    });
    myFixture.checkResult("""
      The first sentence.
      " - the second sentence."
      The third sentence.
      """);
  }

  @NeedsCloud
  @Test
  public void testGermanGender() {
    HighlightingTest.enableLanguages(Set.of(Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());
    myFixture.configureByText("a.txt", """
      Viele <STYLE_SUGGESTION descr="Grazie.RuleEngine.De.Style.GENDERN_STYLE"><caret>ArbeiterInnen</STYLE_SUGGESTION>
      und <STYLE_SUGGESTION>Schüler*innen</STYLE_SUGGESTION> waren da.
      """);
    myFixture.checkHighlighting();

    assertOrderedEquals(
      ContainerUtil.map(myFixture.getAvailableIntentions(), i -> i.getText()).subList(0, 4),
      "Gendersternchen (Schüler*innen) überall verwenden",
      "Binnen-I (SchülerInnen) überall verwenden",
      "Geschlechtsspezifische Schreibweise konfigurieren",
      "Edit inspection profile setting"
    );
  }

  @Test
  public void testPythonGoogleCodeStyleDoc() {
    myFixture.configureByText("a.py", """
      class C:
        \"""Some first sentence, which is long enough to be language-detected.
      
        Args:
           token: Some sentence about a token, which is also long enough to be language-detected.
             Another sentence about token.
      
           another_arg: Some sentence about another argument, which is also long enough to be language-detected.
             Another sentence about this second argument.
      
        \"""
        pass
      
      class D:
        \"""
          One sentence that is capitalized.
          <STYLE_SUGGESTION>an</STYLE_SUGGESTION>other sentence, which is not so capitalized.
        \"""
        pass
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testPythonRestructuredTextDoc() {
    myFixture.configureByText("a.py", """
      def function_name(parameter_1: int, parameter_2: float) -> float:
                ""\"
                Brief description of what this function does.
                :param parameter_2: Brief description of parameter 1.
                :param parameter_1: Brief description of parameter 2.
                :return: Brief description of this function's return value.
                ""\"
                pass
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testPythonNumpydoc() {
    myFixture.configureByText("a.py", """
      def add(a, b):
                  ""\"
                  Add two numbers.
      
                  Parameters
                  ----------
                  a : int or float
                      First number to add.
                  b : int or float
                      Second number to add.
                  ""\"
                  return a + b
      """);
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testNoExceptionsInLongJavadocSentenceCapitalization() {
    myFixture.configureByText("a.java", """
      /**
       * A capitalized sentence in a long enough text with large inter-sentence breaks.\s
       * Another capitalized sentence.                                                 \s
       * <STYLE_SUGGESTION>a </STYLE_SUGGESTION>non-capitalized one.
       */
      class C {}
      """);
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testNoExceptionsInTxtSentenceCapitalizationWithNonParsedLineSeparators() {
    myFixture.configureByText("a.txt", """
      Header.
      ---------------------
      
      A capitalized sentence in a long enough text.
      Another capitalized sentence.
      ---------------
      
      Header.
      ----------------
      
      Yet another capitalized sentence, long enough.
      <STYLE_SUGGESTION>mo</STYLE_SUGGESTION>re.
      """);
    myFixture.checkHighlighting();
  }
}
