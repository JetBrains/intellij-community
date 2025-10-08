package com.intellij.grazie.text

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.utils.TextStyleDomain

class TextStyleDomainTest: GrazieTestBase() {

  fun `test honor domain settings`() {
    GrazieConfig.update {
      it.copy(styleProfile = "Academic")
        .withDomainDisabledRules(TextStyleDomain.CodeComment, setOf("LanguageTool.EN.EN_A_VS_AN"))
        .withDomainDisabledRules(TextStyleDomain.CodeDocumentation, setOf("LanguageTool.EN.EN_A_VS_AN"))
    }
    myFixture.configureByText("C.java", """
      /**
       * It is an cat of human
       */
      class C {
          // It is an friend of human
          void foo(int x) {}
      }
    """.trimIndent())
    myFixture.checkHighlighting()

    myFixture.configureByText(".md", """
      It is <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> dog of human
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}