package com.intellij.cce.java.chat

import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TokenProperties
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class InEditorGeneratedCodeIntegratorTest : BasePlatformTestCase() {
  fun `test integrate inserts generated method at caret`() {
    val fileContent = """
            public class Test {
                public void existingMethod() {
                    System.out.println("Existing method");
                }
            }
        """.trimIndent()
    myFixture.configureByText("Test.java", fileContent)
    val editor: Editor = myFixture.editor
    val caretPosition = fileContent.length - 1
    ApplicationManager.getApplication().runWriteAction {
      editor.caretModel.moveToOffset(caretPosition)
    }

    val codeToGenerate = """
        class GeneratedClass {
            @Generated
            public void newMethod() {
                System.out.println("Generated method");
            }
        }
        """.trimIndent()

    val tokenProperties = createTokenProperties()

    val integrator = InEditorGeneratedCodeIntegrator()
    val result = runBlocking {
      integrator.integrate(project, codeToGenerate, tokenProperties)
    }

    val expectedText = """
            public class Test {
                public void existingMethod() {
                    System.out.println("Existing method");
                }
            @Generated
                public void newMethod() {
                    System.out.println("Generated method");
                }
            }
        """.trimIndent()
    assertEquals(expectedText, result)
  }

  private fun createTokenProperties(): TokenProperties {
    val tokenProperties = object : TokenProperties {
      override val tokenType = TypeProperty.UNKNOWN
      override val location = SymbolLocation.UNKNOWN
      override fun additionalProperty(name: String): String? {
        return if (name == METHOD_NAME_PROPERTY) "newMethod" else null
      }

      override fun additionalPropertyNames() = setOf(METHOD_NAME_PROPERTY)
      override fun describe() = "Test Token Properties"
      override fun hasFeature(feature: String) = false
      override fun withFeatures(features: Set<String>) = this
    }
    return tokenProperties
  }
}