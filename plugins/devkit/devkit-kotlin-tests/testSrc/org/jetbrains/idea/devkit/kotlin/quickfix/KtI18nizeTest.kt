// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.quickfix

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInspection.i18n.I18nQuickFixHandler
import com.intellij.codeInspection.i18n.I18nizeAction
import com.intellij.codeInspection.i18n.I18nizeConcatenationQuickFix
import com.intellij.codeInspection.i18n.JavaI18nUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UStringConcatenationsFacade.Companion.createFromTopConcatenation
import org.jetbrains.uast.toUElementOfType
import org.junit.Assert
import java.util.*

private const val i18nizedExpr = "i18nizedExpr"

/**
 * Analogical Java tests: [com.intellij.java.codeInsight.daemon.quickFix.I18nizeTest]
 */
class KtI18nizeTest : LightJavaCodeInsightFixtureTestCase() {

  private fun <T : UExpression> doTest(before: String, expected: String? = null, i18nized: String = i18nizedExpr) {
    myFixture.configureByText("Test.kt", before)
    val action = I18nizeAction()
    val dataContext = DataManager.getInstance().getDataContext(editor.component)
    val event = AnActionEvent.createFromAnAction(action, null, "place", dataContext)
    action.update(event)
    val handler: I18nQuickFixHandler<T>? = I18nizeAction.getHandler(event) as I18nQuickFixHandler<T>?
    handler?.checkApplicability(file, editor)
    TestCase.assertEquals(expected != null, event.presentation.isEnabled)
    if (expected != null) {
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        assertNotNull(handler)
        val literalExpression = handler!!.getEnclosingLiteral(file, editor)
        handler.performI18nization(
          file,
          editor,
          literalExpression,
          emptyList(),
          "key1",
          "value1",
          i18nized,
          emptyArray(),
          JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER
        )
      }
      myFixture.checkResult(expected)
    }
  }

  @JvmName("doTestWithoutGeneric")
  private fun doTest(before: String, expected: String? = null, i18nized: String = i18nizedExpr) =
    doTest<UExpression>(before, expected, i18nized)

  fun testLiteral() = doTest("""
    fun main() {
      val foo = "str<caret>ing"
    }
  """.trimIndent(), """
    fun main() {
      val foo = $i18nizedExpr
    }
  """.trimIndent())

  fun testConcatenation() = doTest("""
    fun main() {
      val foo = "str<caret>ing" + "string1"
    }
  """.trimIndent(), """
    fun main() {
      val foo = $i18nizedExpr
    }
  """.trimIndent())

  fun testInterpolation() = doTest("""
    fun main() {
      val bar = "bar"
      val foo = "str<caret>ing ${'$'}bar string1"
    }
  """.trimIndent(), """
    fun main() {
      val bar = "bar"
      val foo = $i18nizedExpr
    }
  """.trimIndent())

  fun testConcatenationWithInterpolation() = doTest("""
    fun main() {
      val bar = "bar"
      val foo = "str<caret>ing ${'$'}bar string1" + bar
    }
  """.trimIndent(), """
    fun main() {
      val bar = "bar"
      val foo = $i18nizedExpr
    }
  """.trimIndent())

  fun testOutsideLiteral() = doTest("""
    fun main() {
      <caret>val foo = "string"
    }
  """.trimIndent())

  fun testShortenClassReferences() = doTest("""
    package p
    class MyBundle {
      fun message(key: String): String {
        return key;
      }
    }
    class a {
      fun f() {
        val s = "x<caret>xxxx"
      }
    }
  """.trimIndent(), """
    package p
    class MyBundle {
      fun message(key: String): String {
        return key;
      }
    }
    class a {
      fun f() {
        val s = MyBundle().message("key")
      }
    }
  """.trimIndent(), i18nized = """p.MyBundle().message("key")""")

  fun testConcatenationWithIfExpr() {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(prefix : Boolean){
          val s = "Not a valid java identifier<caret> part in " + (if (prefix) "prefix" else "suffix"))
        }
      }
    """.trimIndent())
    val enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(file, editor)
    val concatenation = createFromTopConcatenation(enclosingStringLiteral)
    assertNotNull(concatenation)
    val args = ArrayList<UExpression?>()
    Assert.assertEquals("Not a valid java identifier part in {0, choice, 0#prefix|1#suffix}",
                        JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, project))
    assertSize(1, args)
    assertEquals("if (prefix) 0 else 1", args[0]!!.sourcePsi!!.text)
  }
  
  fun testConcatenationWithIfExprNested() {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(list : java.util.List<String>){
          val s = "Not a valid java identifier<caret> part in " + (if (list.size() == 1) list.get(0) + " prefix's" else "suffix's"))
        }
      }
    """.trimIndent())
    val enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(file, editor)
    val concatenation = createFromTopConcatenation(enclosingStringLiteral)
    assertNotNull(concatenation)
    val args = ArrayList<UExpression?>()
    Assert.assertEquals("Not a valid java identifier part in {1, choice, 0#{0} prefix''''s|1#suffix''''s}",
                        JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, project))
    assertSize(2, args)
    assertEquals("list.get(0)", args[0]!!.sourcePsi!!.text)
    assertEquals("if (list.size() == 1) 0 else 1", args[1]!!.sourcePsi!!.text)
  }

  fun testConcatenationWithIfExprNested1() {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(list : java.util.List<String>){
          val s = "Not a valid java identifier part in " + (if (list.size() == 1) list.get(0) + " prefix's" else "su<caret>ffix's"))
        }
      }
    """.trimIndent())
    val enclosingStringLiteral = I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(myFixture.file.findElementAt(myFixture.editor.caretModel.offset))
    
    val concatenation = createFromTopConcatenation(enclosingStringLiteral)
    assertNotNull(concatenation)
    val args = ArrayList<UExpression?>()
    Assert.assertEquals("Not a valid java identifier part in {1, choice, 0#{0} prefix''''s|1#suffix''''s}",
                        JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, project))
    assertSize(2, args)
    assertEquals("list.get(0)", args[0]!!.sourcePsi!!.text)
    assertEquals("if (list.size() == 1) 0 else 1", args[1]!!.sourcePsi!!.text)
  }
  
  fun testConcatenationOfLiterals() {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(prefix : Boolean){
          val s = "<caret>part in " + "suffix {0} and prefix '{1}'"
        }
      }
    """.trimIndent())
    val enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(file, editor)
    val concatenation = createFromTopConcatenation(enclosingStringLiteral)
    assertNotNull(concatenation)
    val args = ArrayList<UExpression?>()
    Assert.assertEquals("part in suffix {0} and prefix '{1}'",
                        JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, project))
  }

  fun testLightAnnotationTypeResolve() {
    myFixture.configureByText("Test.kt", """
      annotation class MyMetaAnnotation
      
      @MyMetaAnnotation
      annotation class MyAnnotation

      @MyAnnotation
      class MyClass<caret>
    """.trimIndent())

    val psiClass = myFixture.elementAtCaret.toUElementOfType<UClass>()?.javaPsi
                   ?: error("PsiClass not found")

    val resolvedAnnotationType = psiClass.annotations.firstOrNull()?.resolveAnnotationType()
    assertNull(resolvedAnnotationType)

    assertTrue(MetaAnnotationUtil.isMetaAnnotated(psiClass, listOf("MyMetaAnnotation")))
  }
}
