package com.intellij.grazie.pro;

import ai.grazie.nlp.langs.Language;
import ai.grazie.rules.en.EnglishParameters;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection;
import com.intellij.grazie.jlanguage.Lang;
import com.intellij.grazie.text.TreeRuleChecker;
import com.intellij.grazie.utils.TextStyleDomain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("NonAsciiCharacters")
public class TypographyTest extends BaseTestCase {

  @BeforeEach
  public void setUp() {
    myFixture.enableInspections(GrazieInspection.class, GrazieInspection.Grammar.class, GrazieInspection.Style.class);
  }

  @NeedsCloud
  @Test
  public void testDashesAreSuggestedInMd() {
    GrazieConfig.Companion.update(config -> config.withAutoFix(false));
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.RUSSIAN), getProject(), getTestRootDisposable());

    myFixture.configureByText("a.md", """
    A Song for <STYLE_SUGGESTION>Spain<caret> - Consider</STYLE_SUGGESTION> what it would be like to have a national anthem without lyrics.
    That is Vitorino Nemésio (<STYLE_SUGGESTION>1901 - 1978</STYLE_SUGGESTION>) — writer and university teacher.
  
    Дефис и <STYLE_SUGGESTION>тире - это</STYLE_SUGGESTION> совсем разные знаки в русском языке.
  """.stripIndent());
    myFixture.checkHighlighting();
    assertNotNull(findSingleIntention("Use a dash"));
    myFixture.launchAction(findSingleIntention("Spain – Consider"));
    assertTrue(myFixture.getEditor().getDocument().getText().contains("Spain – Consider"));
  }

  @NeedsCloud
  @Test
  public void testPlainTextDashesAreNotSuggestedInComments() {
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.RUSSIAN), getProject(), getTestRootDisposable());

    myFixture.configureByText("a.java", """
    // A Song for Spain - Consider what it would be like to have a national anthem without lyrics.
    // A Song for Spain -- Consider what it would be like to have a national anthem without lyrics.
    // That is Vitorino Nemésio (1901 - 1978) — writer and university teacher.
  
    // Дефис и тире - это совсем разные знаки в русском языке.
    // Дефис и тире -- это совсем разные знаки в русском языке.
  """.stripIndent());
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testUnicodeArrowContexts() {
    myFixture.configureByText("a.java", "// Arrows -> should not be suggested in the code");
    myFixture.checkHighlighting();

    myFixture.configureByText("a.md", "Arrows <STYLE_SUGGESTION descr=\"Grazie.RuleEngine.En.Typography.ASCII_APPROXIMATIONS\">-></STYLE_SUGGESTION> should be suggested in rich text");
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testSmartApostropheInMarkdown() {
    useSmartApostrophes();
    myFixture.configureByText("a.md", "<STYLE_SUGGESTION>Don't</STYLE_SUGGESTION> cry for me Argentina");
    myFixture.checkHighlighting();
    myFixture.launchAction(findSingleIntention("Don’t"));
    myFixture.checkResult("Don’t cry for me Argentina");
  }

  @NeedsCloud
  @Test
  public void testSmartApostropheInHtml() {
    useSmartApostrophes();
    myFixture.configureByText("a.html", "<b>Hello. <STYLE_SUGGESTION descr=\"Grazie.RuleEngine.En.Typography.SMART_APOSTROPHE\">Don't</STYLE_SUGGESTION> cry for me Argentina</b>");
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testSmartApostropheInProperties() {
    useSmartApostrophes();
    myFixture.configureByText("a.properties", "with.apos=<STYLE_SUGGESTION descr=\"Grazie.RuleEngine.En.Typography.SMART_APOSTROPHE\">Don</STYLE_SUGGESTION>'<STYLE_SUGGESTION descr=\"Grazie.RuleEngine.En.Typography.SMART_APOSTROPHE\">'t</STYLE_SUGGESTION> cry for me Argentina");
    myFixture.checkHighlighting();
  }

  @Test
  public void testNoSmartTypographyInTxtForNow() { // txt files are too versatile and are often expected to be in ASCII
    useSmartApostrophes();
    myFixture.configureByText("a.txt", "Don't cry for me Argentina -> also a song");
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testInsertSmartApostrophesWhenEnabledInMarkdown() {
    useSmartApostrophes();
    myFixture.configureByText("a.md", "I <caret><GRAMMAR_ERROR>believe</GRAMMAR_ERROR> in justice anymore.");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("don’t believe"));
    myFixture.checkResult("I don’t believe in justice anymore.");
  }

  @NeedsCloud
  @Test
  public void testAlwaysInsertPlainApostrophesInCode() {
    useSmartApostrophes();
    myFixture.configureByText("a.java", "// I <caret><GRAMMAR_ERROR>believe</GRAMMAR_ERROR> in justice anymore.");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("don't believe"));
    myFixture.checkResult("// I don't believe in justice anymore.");
  }

  private static void useSmartApostrophes() {
    HighlightingTest.enableRules(TreeRuleChecker.SMART_APOSTROPHE);
  }

  @NeedsCloud
  @Test
  public void testSmartQuoteContexts() {
    HighlightingTest.enableLanguages(Set.of(Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());

    // plain quotes in code, which prefers ASCII
    myFixture.configureByText("a.java", "// Wo ist das <GRAMMAR_ERROR descr=\"Grazie.RuleEngine.De.Spelling.WORD_SEPARATION\"><caret>Gefällt mir Teil</GRAMMAR_ERROR>?");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("\"Gefällt mir"));
    myFixture.checkResult("// Wo ist das \"Gefällt mir\"-Teil?");

    // plain quotes in txt, which is too versatile a format
    myFixture.configureByText("a.txt", "Wo ist das <GRAMMAR_ERROR><caret>Gefällt mir Teil</GRAMMAR_ERROR>?");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("\"Gefällt mir"));
    myFixture.checkResult("Wo ist das \"Gefällt mir\"-Teil?");

    // smart quotes in md
    myFixture.configureByText("a.md", "Wo ist das <GRAMMAR_ERROR><caret>Gefällt mir Teil</GRAMMAR_ERROR>?");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("„Gefällt"));
    myFixture.checkResult("Wo ist das „Gefällt mir“-Teil?");
  }

  @NeedsCloud
  @Test
  public void testNbspInMarkdown() {
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());

    myFixture.configureByText("a.md",
      "Die Datei wird benötigt (<STYLE_SUGGESTION descr=\"Grazie.RuleEngine.De.Typography.ABBREVIATION_SPACES\"><caret>z.B.</STYLE_SUGGESTION> aus welchem Sourcestand die Anwendung gebaut wurde)."
    );
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("z. B."));
    myFixture.checkResult("Die Datei wird benötigt (z. B. aus welchem Sourcestand die Anwendung gebaut wurde).");
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testNoNbspInComments() {
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());

    myFixture.configureByText("a.java", "// Die Datei wird benötigt (<STYLE_SUGGESTION descr=\"Grazie.RuleEngine.De.Typography.ABBREVIATION_SPACES\">z.<caret>B.</STYLE_SUGGESTION> aus welchem Sourcestand die Anwendung gebaut wurde).");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("z. B."));
    myFixture.checkResult("// Die Datei wird benötigt (z. B. aus welchem Sourcestand die Anwendung gebaut wurde).");
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testNoThinNbspInComments() {
    GrazieConfig.Companion.update(s -> s.withParameter(TextStyleDomain.CodeComment, Language.ENGLISH, EnglishParameters.NUMBER_FORMATTING, "narrowNbsp"));

    myFixture.configureByText("a.java", "// There are exactly <STYLE_SUGGESTION descr=\"Grazie.RuleEngine.En.Typography.NUMBER_FORMATTING\"><caret>134535314</STYLE_SUGGESTION> of them.");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("134 535 314"));
    myFixture.checkResult("// There are exactly 134 535 314 of them.");
    myFixture.checkHighlighting();
  }

  @NeedsCloud
  @Test
  public void testNoGermanEllipsisNbsp() {
    HighlightingTest.enableLanguages(Set.of(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN), getProject(), getTestRootDisposable());

    myFixture.configureByText("a.java", "// Was das wohl <caret><GRAMMAR_ERROR descr=\"Grazie.RuleEngine.De.Punctuation.FORMATTING_ISSUES\">bedeutet...</GRAMMAR_ERROR>");
    myFixture.checkHighlighting();
    myFixture.launchAction(myFixture.findSingleIntention("bedeutet ..."));
    myFixture.checkResult("// Was das wohl bedeutet ...");
    myFixture.checkHighlighting();
  }
}
