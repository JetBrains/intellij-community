package com.intellij.grazie.pro

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import com.intellij.openapi.util.registry.Registry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class CorrectTextTest : BaseTestCase() {

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GrazieSpellCheckingInspection::class.java, GrazieInspection.Grammar::class.java, GrazieInspection.Style::class.java)
    Registry.get("grazie.correct.text.enabled").setValue(true, testRootDisposable)
  }

  @Test
  @NeedsCloud
  fun `test text-level issues are found`() {
    myFixture.configureByText("a.java", """
    class A {
        // Here is <TYPO descr="Typo: In word 'typpo'">typpo</TYPO>. A little bit more context for a detector.

        // <TYPO descr="Typo: In word 'Typppo'">Typppo</TYPO>.

        private static final int TEST = 123;

        // There is <GRAMMAR_ERROR descr="Grazie.MLEC.En.MissingArticle: Missing article">missing article</GRAMMAR_ERROR>. Can you find it? <GRAMMAR_ERROR descr="Grazie.RuleEngine.En.Grammar.SUBJECT_VERB_AGREEMENT">Jim get</GRAMMAR_ERROR> over here!
        
        public static void main() {
            int a = 123;
        }
        
        // This is a <TYPO descr="Typo: In word 'typpo'">typpo</TYPO>, can you find me?

        // This is sentence number one. inconsistent capitalization.
    }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

}
