package com.intellij.cce.java.chat

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


    val integrator = InEditorGeneratedCodeIntegrator()
    val result = runBlocking {
      integrator.integrate(project, codeToGenerate)
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
}