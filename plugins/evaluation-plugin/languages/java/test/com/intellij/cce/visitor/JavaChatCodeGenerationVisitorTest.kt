package com.intellij.cce.visitor

import com.intellij.cce.evaluable.EXTERNAL_API_CALLS_PROPERTY
import com.intellij.cce.evaluable.INTERNAL_API_CALLS_PROPERTY
import com.intellij.cce.evaluable.INTERNAL_RELEVANT_FILES_PROPERTY
import com.intellij.cce.evaluable.METHOD_NAME_PROPERTY
import com.intellij.cce.java.visitor.JavaChatCodeGenerationVisitor
import com.intellij.cce.util.extractAdditionalProperties
import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import kotlinx.coroutines.test.runTest
import kotlin.io.path.Path
import kotlin.io.path.relativeTo


class JavaChatCodeGenerationVisitorTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath(): String = "plugins/evaluation-plugin/languages/java/testData"

  fun `test chat code generation visitor handles external API calls`() = runTest {
    val visitor = JavaChatCodeGenerationVisitor()
    val psiFile = prepareFileWithExternalApiCall()

    readAction { psiFile.accept(visitor) }

    val expectedAdditionalProperties = setOf(
      mapOf(
        METHOD_NAME_PROPERTY to "processString",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to "",
        EXTERNAL_API_CALLS_PROPERTY to "capitalize",
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "isStringEmpty",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to "",
        EXTERNAL_API_CALLS_PROPERTY to "isEmpty",
      )
    )
    val actualAdditionalProperties = visitor.getFile().extractAdditionalProperties()
    assertEquals(expectedAdditionalProperties, actualAdditionalProperties)
  }


  fun `test chat code generation visitor extracts all that is needed`() = runTest {
    val visitor = JavaChatCodeGenerationVisitor()
    val psiFile = prepareFile()

    readAction { psiFile.accept(visitor) }


    val expectedAdditionalProperties = setOf(
      mapOf(
        METHOD_NAME_PROPERTY to "foo",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to "",
        EXTERNAL_API_CALLS_PROPERTY to "",
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "MyClass",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to "",
        EXTERNAL_API_CALLS_PROPERTY to "",
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "myMethod",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to "",
        EXTERNAL_API_CALLS_PROPERTY to "",
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "SuperClass",
        INTERNAL_API_CALLS_PROPERTY to "",
        INTERNAL_RELEVANT_FILES_PROPERTY to "",
        EXTERNAL_API_CALLS_PROPERTY to "",
      ),
      mapOf(
        METHOD_NAME_PROPERTY to "myMethod",
        INTERNAL_API_CALLS_PROPERTY to "SuperClass#foo",
        INTERNAL_RELEVANT_FILES_PROPERTY to psiFile.projectRelativePath(),
        EXTERNAL_API_CALLS_PROPERTY to "",
      )
    )
    val actualAdditionalProperties = visitor.getFile().extractAdditionalProperties()
    assertEquals(expectedAdditionalProperties, actualAdditionalProperties)
  }

  private fun PsiFile.projectRelativePath(): String {
    return Path(this.virtualFile.path).relativeTo(Path(project.basePath!!)).toString()
  }

  private fun prepareFileWithExternalApiCall(): PsiFile {
    val code = """
        import org.apache.commons.lang3.StringUtils;
        import org.apache.commons.lang3.StringUtils.isEmpty;
        
        public class ExternalApiExample {
            public String processString(String input) {
                return StringUtils.capitalize(input);
            }
            
            public Boolean isStringEmpty(String input) {
                System.out.println("Hello from isStringEmpty");
                return isEmpty(input);
            }
        }
    """.trimIndent()
    return myFixture.createPsiFile(code)
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