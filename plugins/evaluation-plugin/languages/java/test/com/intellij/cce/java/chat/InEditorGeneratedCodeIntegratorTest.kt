package com.intellij.cce.java.chat

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class InEditorGeneratedCodeIntegratorTest : BasePlatformTestCase() {
  fun `test integrate inserts generated method at caret`() {
    val fileContent = """
            public class Test {
                public void existingMethod() {
                    System.out.println("Existing method");
                }
                <caret>
            }
        """.trimIndent()
    myFixture.configureByText("Test.java", fileContent)

    val codeToGenerate = """
            public void newMethod() {
                System.out.println("Generated method");
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
                public void newMethod() {
                System.out.println("Generated method");
            }
            
            }
        """.trimIndent()
    assertEquals(expectedText, result)
  }
}