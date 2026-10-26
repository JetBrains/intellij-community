package com.intellij.grazie.ide

import ai.grazie.nlp.langs.Language
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.getLanguageIfAvailable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class LanguageDetectionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test text in the middle is not French`() {
    grazie.enableProofreadingFor(setOf(Lang.FRENCH, Lang.AMERICAN_ENGLISH))
    grazie.fixture.configureByText("a.java", """
      // use multiple instances within container
      
      // use multiple containers
      
      // use concurrent pinging within PF instance, using a worker pool
    """.trimIndent())
    grazie.fixture.checkHighlighting()

    runReadActionBlocking {
      HighlightingUtil.getAllFileTexts(grazie.fixture.file.viewProvider)
        .forEach {
          val contentLanguage = getLanguageIfAvailable(it)
          assertEquals(
            Language.ENGLISH, contentLanguage,
            "Language of `$it` expected to be English, not $contentLanguage"
          )
        }
    }
  }
}
