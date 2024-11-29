package com.intellij.cce.visitor


import com.intellij.cce.java.chat.extractCalledInternalApiMethods
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.PsiFileFactoryImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class JavaApiCallExtractorKtTest : BasePlatformTestCase() {
  private lateinit var psiFileFactory: PsiFileFactoryImpl
  private lateinit var psiElementFactory: PsiElementFactory

  override fun setUp() {
    super.setUp()
    psiFileFactory = PsiFileFactoryImpl.getInstance(project) as PsiFileFactoryImpl
    psiElementFactory = PsiElementFactory.getInstance(project)
  }

  fun `test extract internal API methods within project`() {
    val code = """
            public class MyClass {
                public void internalMethod() {
                    System.out.println("Hello world");
                }
            
                public void myMethod() {
                  internalMethod(); 
                }
            }
        """.trimIndent()
    val psiFile = createPsiFile(code)
    val internalMethods = extractCalledInternalApiMethods(psiFile)

    assertSize(1, internalMethods)
    assertEquals("internalMethod", internalMethods[0].name)
  }

  fun `test no internal API methods`() {
    val code = """
            public class MyClass {
                public void myMethod() {
                    System.out.println("Hello world");
                }
            }
        """.trimIndent()
    val psiFile = createPsiFile(code)
    val internalMethods = extractCalledInternalApiMethods(psiFile)

    assertEmpty(internalMethods)
  }

  fun `test super calls, new expressions and inside constructors are not considered`() {
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
    val psiFile = createPsiFile(code)
    val internalMethods = extractCalledInternalApiMethods(psiFile)

    assertSize(1, internalMethods)
    assertEquals("foo", internalMethods[0].name)
  }

  private fun createPsiFile(code: String): PsiFile {
    val project = myFixture.project
    val virtualFile = myFixture.tempDirFixture.createFile("MyClass.java", code)

    lateinit var psiFile: PsiFile
    ApplicationManager.getApplication().invokeAndWait {
      WriteCommandAction.runWriteCommandAction(project) {
        psiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
      }
    }
    return psiFile
  }
}