package com.intellij.grazie.ide

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.getLanguageIfAvailable

internal class LanguageDetectionTest: GrazieTestBase() {

  fun `test text in the middle is not French`() {
    enableProofreadingFor(setOf(Lang.FRENCH, Lang.AMERICAN_ENGLISH))
    myFixture.configureByText("a.java", """
      // use multiple instances within container
      
      // use multiple containers
      
      // use concurrent pinging within PF instance, using a worker pool
    """.trimIndent())
    myFixture.checkHighlighting()

    HighlightingUtil.getAllFileTexts(myFixture.file.viewProvider)
      .forEach {
        val contentLanguage = getLanguageIfAvailable(it)
        assertEquals(
          "Language of `$it` expected to be English, not $contentLanguage",
          Language.ENGLISH, contentLanguage
        )
      }
  }
}
