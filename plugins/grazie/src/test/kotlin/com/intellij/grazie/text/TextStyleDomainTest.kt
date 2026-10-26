package com.intellij.grazie.text

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.utils.TextStyleDomain
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@TestApplication
class TextStyleDomainTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @Suppress("unused")
  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test honor domain settings`() {
    GrazieConfig.update {
      it.copy(styleProfile = "Academic")
        .withDomainDisabledRules(TextStyleDomain.CodeComment, setOf("LanguageTool.EN.EN_A_VS_AN"))
        .withDomainDisabledRules(TextStyleDomain.CodeDocumentation, setOf("LanguageTool.EN.EN_A_VS_AN"))
    }
    grazie.fixture.configureByText("C.java", """
      /**
       * It is an cat of human
       */
      class C {
          // It is an friend of human
          void foo(int x) {}
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()

    grazie.fixture.configureByText(".md", """
      It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> dog of human
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }
}