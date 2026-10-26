package com.intellij.grazie.suppression

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@TestApplication
class SuppressionTests {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  @Suppress("unused")
  private val grazie by grazieTestFixture(projectFixture, projectDescriptor = JAVA_LATEST)

  @Test
  fun `test suppressing style inspection doesn't affect grammar issues`() {
    grazie.fixture.configureByText("A.java", """
        class A {
          // Show is cancelled because of <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> cat
          @SuppressWarnings("GrazieStyle")
          public static void main() {}
        }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test suppressing grammar inspection doesn't affect style issues`() {
    grazie.fixture.configureByText("A.java", """
        class A {
          // Show is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">can<caret>celled</STYLE_SUGGESTION> because of an cat
          @SuppressWarnings("GrazieInspection")
          public static void main() {}
        }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test suppress style action generates necessary inspection id`() {
    grazie.fixture.configureByText("Class.java", """
      public class Class {
          // Show is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">can<caret>celled</STYLE_SUGGESTION>
          public static void main() {}
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
    val intention = grazie.fixture.getAvailableIntention("Suppress for method")
    assertNotNull(intention)
    grazie.fixture.launchAction(intention!!)
    grazie.fixture.checkResult("""
      public class Class {
          // Show is cancelled
          @SuppressWarnings("GrazieStyle")
          public static void main() {}
      }
    """.trimIndent())
  }

  @Test
  fun `test suppress grammar action generates necessary inspection id`() {
    grazie.fixture.configureByText("Class.java", """
      public class Class {
          // There is <GRAMMAR_ERROR descr="EN_A_VS_AN">a<caret>n</GRAMMAR_ERROR> cat
          public static void main() {}
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
    val intention = grazie.fixture.getAvailableIntention("Suppress for class")
    assertNotNull(intention)
    grazie.fixture.launchAction(intention!!)
    grazie.fixture.checkResult("""
      @SuppressWarnings("GrazieInspection")
      public class Class {
          // There is an cat
          public static void main() {}
      }
    """.trimIndent())
  }

  @Test
  fun `test disabling grammar inspection`() {
    grazie.fixture.disableInspections(GrazieInspection.Grammar())
    grazie.fixture.configureByText("Class.java", """
      public class Class {
          // Show is <STYLE_SUGGESTION descr="Grazie.RuleEngine.En.Style.VARIANT_LEXICAL_DIFFERENCES">can<caret>celled</STYLE_SUGGESTION> because of an cat
          public static void main() {}
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }

  @Test
  fun `test disabling style inspection`() {
    grazie.fixture.disableInspections(GrazieInspection.Style())
    grazie.fixture.configureByText("Class.java", """
      public class Class {
          // Show is cancelled because of <GRAMMAR_ERROR descr="EN_A_VS_AN">an</GRAMMAR_ERROR> cat
          public static void main() {}
      }
    """.trimIndent())
    grazie.fixture.checkHighlighting()
  }
}