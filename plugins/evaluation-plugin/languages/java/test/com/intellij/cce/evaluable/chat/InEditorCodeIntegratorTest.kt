package com.intellij.cce.evaluable.chat

import com.intellij.cce.java.chat.InEditorGeneratedCodeIntegrator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking

class InEditorCodeIntegratorTest : BasePlatformTestCase() {
  fun `test when no imports`() {
    val existingCode = """
            public class MyClass {
                public void bar(Integer t) {
                  bar();
                }
                <caret>
            }
        """.trimIndent()

    myFixture.configureByText("MyClass.java", existingCode)

    val code = """
            public void bar() {
                foo();
            }
        """.trimIndent()

    val expectedResult = """
            public class MyClass {
                public void bar(Integer t) {
                  bar();
                }
                public void bar() {
                foo();
            }
            
            }
    """.trimIndent()
    val result = runBlocking { InEditorGeneratedCodeIntegrator().integrate(project, code, listOf()) }
    assertEquals(expectedResult, result)
  }

  fun `test when with imports`() {
    val existingCode = """
            public class MyClass {
                public void bar(Integer t) {
                  bar();
                }
                <caret>
            }
        """.trimIndent()

    myFixture.configureByText("MyClass.java", existingCode)

    val code = """
            public void bar() {
                List.of();
            }
        """.trimIndent()

    val expectedResult = """
            import java.util.List;
            
            public class MyClass {
                public void bar(Integer t) {
                  bar();
                }
                public void bar() {
                List.of();
            }
            
            }
    """.trimIndent()
    val result = runBlocking { InEditorGeneratedCodeIntegrator().integrate(project, code, listOf("import java.util.List;")) }
    assertEquals(expectedResult, result)
  }

  fun `test when with package`() {
    val existingCode = """
            package com.example;
            
            public class MyClass {
                public void bar(Integer t) {
                  bar();
                }
                <caret>
            }
        """.trimIndent()

    myFixture.configureByText("MyClass.java", existingCode)

    val code = """
            public void bar() {
                foo();
            }
        """.trimIndent()

    val expectedResult = """
            package com.example;
            
            public class MyClass {
                public void bar(Integer t) {
                  bar();
                }
                public void bar() {
                foo();
            }
            
            }
    """.trimIndent()
    val result = runBlocking { InEditorGeneratedCodeIntegrator().integrate(project, code, listOf()) }
    assertEquals(expectedResult, result)
  }

}