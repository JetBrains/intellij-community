package com.intellij.cce.visitor

import com.intellij.cce.evaluable.INTERNAL_API_CALLS_PROPERTY
import com.intellij.cce.evaluable.INTERNAL_RELEVANT_FILES_PROPERTY
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.cce.java.visitor.JavaChatCodeGenerationVisitor
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.test.runTest


class JavaChatCodeGenerationVisitorTest : BasePlatformTestCase() {
  override fun getBasePath(): String = "plugins/evaluation-plugin/languages/java/testData"


  fun `test chat code generation visitor extracts all that is needed`() = runTest {
    val visitor = JavaChatCodeGenerationVisitor()
    val psiFile = prepareFile()

    readAction { psiFile.accept(visitor) }


    val expectedAdditionalProperties = setOf(
      mapOf(
        METHOD_NAME_PROPERTY to "foo",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to ""
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "MyClass",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to ""
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "myMethod",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to ""
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "SuperClass",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to ""
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "myMethod",
        INTERNAL_API_CALLS_PROPERTY to "SuperClass#foo",
        INTERNAL_RELEVANT_FILES_PROPERTY to "../../../../../../../src/MyClass.java"
      )
    )
    val actualAdditionalProperties = visitor.getFile().extractAdditionalProperties()
    assertEquals(expectedAdditionalProperties, actualAdditionalProperties)
  }

  private fun prepareFile(): PsiFile {
    val code = """
        public class MyClass extends SuperClass {
            public MyClass() {
                super();
            }

            @Override
            public void myMethod() {
                new SuperClass();
                super.myMethod();
            }
        }
        
        public class SuperClass {
            public SuperClass() {}
            public void foo() {}  
            public void myMethod() {
                foo() {}
                System.out.println("Hello from SuperClass");
            }
        }
    """.trimIndent()
    return myFixture.createPsiFile(code)
  }
}