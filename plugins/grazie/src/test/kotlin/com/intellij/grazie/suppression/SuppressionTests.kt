package com.intellij.grazie.suppression

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST

class SuppressionTests: GrazieTestBase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_LATEST

  fun `test suppressing style inspection doesn't affect grammar issues`() {
    myFixture.configureByText("A.java", """
        class A {
          // Show is cancelled because of <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> cat
          @SuppressWarnings("GrazieStyle")
          public static void main() {}
        }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test suppressing grammar inspection doesn't affect style issues`() {
    myFixture.configureByText("A.java", """
        class A {
          // Show is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">can<caret>celled</STYLE_SUGGESTION> because of an cat
          @SuppressWarnings("GrazieInspection")
          public static void main() {}
        }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test suppress style action generates necessary inspection id`() {
    myFixture.configureByText("Class.java", """
      public class Class {
          // Show is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">can<caret>celled</STYLE_SUGGESTION>
          public static void main() {}
      }
    """.trimIndent())
    myFixture.checkHighlighting()
    val intention = myFixture.getAvailableIntention("Suppress for method")
    assertNotNull(intention)
    myFixture.launchAction(intention!!)
    myFixture.checkResult("""
      public class Class {
          // Show is cancelled
          @SuppressWarnings("GrazieStyle")
          public static void main() {}
      }
    """.trimIndent())
  }

  fun `test suppress grammar action generates necessary inspection id`() {
    myFixture.configureByText("Class.java", """
      public class Class {
          // There is <GRAMMAR_ERROR descr="EN_A_VS_AN">a<caret>n</GRAMMAR_ERROR> cat
          public static void main() {}
      }
    """.trimIndent())
    myFixture.checkHighlighting()
    val intention = myFixture.getAvailableIntention("Suppress for class")
    assertNotNull(intention)
    myFixture.launchAction(intention!!)
    myFixture.checkResult("""
      @SuppressWarnings("GrazieInspection")
      public class Class {
          // There is an cat
          public static void main() {}
      }
    """.trimIndent())
  }

  fun `test disabling grammar inspection`() {
    myFixture.disableInspections(GrazieInspection.Grammar())
    myFixture.configureByText("Class.java", """
      public class Class {
          // Show is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">can<caret>celled</STYLE_SUGGESTION> because of an cat
          public static void main() {}
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }

  fun `test disabling style inspection`() {
    myFixture.disableInspections(GrazieInspection.Style())
    myFixture.configureByText("Class.java", """
      public class Class {
          // Show is cancelled because of <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> cat
          public static void main() {}
      }
    """.trimIndent())
    myFixture.checkHighlighting()
  }
}