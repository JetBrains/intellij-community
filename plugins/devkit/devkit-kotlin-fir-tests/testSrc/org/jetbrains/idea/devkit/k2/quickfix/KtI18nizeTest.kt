// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.quickfix

import com.intellij.codeInsight.MetaAnnotationUtil
import com.intellij.codeInspection.i18n.I18nQuickFixHandler
import com.intellij.codeInspection.i18n.I18nizeAction
import com.intellij.codeInspection.i18n.I18nizeConcatenationQuickFix
import com.intellij.codeInspection.i18n.JavaI18nUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UStringConcatenationsFacade
import org.jetbrains.uast.toUElementOfType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes

private const val i18nizedExpr = "i18nizedExpr"

/**
 * Analogical Java tests: [com.intellij.java.codeInsight.daemon.quickFix.I18nizeTest]
 */
@TestApplication
class KtI18nizeTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  private fun <T : UExpression> doTest(before: String, expected: String? = null, i18nized: String = i18nizedExpr): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("Test.kt", before)
    withContext(Dispatchers.EDT) {
      val action = I18nizeAction()
      val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
      val event = AnActionEvent.createFromAnAction(action, null, "place", dataContext)
      action.update(event)
      val handler: I18nQuickFixHandler<T>? = I18nizeAction.getHandler(event) as I18nQuickFixHandler<T>?
      handler?.checkApplicability(myFixture.file, myFixture.editor)
      assertEquals(expected != null, event.presentation.isEnabled)
      if (expected != null) {
        WriteCommandAction.runWriteCommandAction(myFixture.project) {
          assertNotNull(handler)
          val literalExpression = handler!!.getEnclosingLiteral(myFixture.file, myFixture.editor)
          handler.performI18nization(
            myFixture.file,
            myFixture.editor,
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
  }

  @JvmName("doTestWithoutGeneric")
  private fun doTest(before: String, expected: String? = null, i18nized: String = i18nizedExpr) =
    doTest<UExpression>(before, expected, i18nized)

  @Test
  fun testLiteral(): Unit = doTest("""
    fun main() {
      val foo = "str<caret>ing"
    }
  """.trimIndent(), """
    fun main() {
      val foo = $i18nizedExpr
    }
  """.trimIndent())

  @Test
  fun testConcatenation(): Unit = doTest("""
    fun main() {
      val foo = "str<caret>ing" + "string1"
    }
  """.trimIndent(), """
    fun main() {
      val foo = $i18nizedExpr
    }
  """.trimIndent())

  @Test
  fun testInterpolation(): Unit = doTest("""
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

  @Test
  fun testConcatenationWithInterpolation(): Unit = doTest("""
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

  @Test
  fun testOutsideLiteral(): Unit = doTest("""
    fun main() {
      <caret>val foo = "string"
    }
  """.trimIndent())

  @Test
  fun testShortenClassReferences(): Unit = doTest("""
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

  @Test
  fun testConcatenationWithIfExpr(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(prefix : Boolean){
          val s = "Not a valid java identifier<caret> part in " + (if (prefix) "prefix" else "suffix"))
        }
      }
    """.trimIndent())
    readAction {
      val enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(myFixture.file, myFixture.editor)
      val concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral)
      assertNotNull(concatenation)
      val args = ArrayList<UExpression?>()
      assertEquals("Not a valid java identifier part in {0, choice, 0#prefix|1#suffix}",
                   JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, myFixture.project))
      assertEquals(1, args.size)
      assertEquals("if (prefix) 0 else 1", args[0]!!.sourcePsi!!.text)
    }
  }

  @Test
  fun testConcatenationWithIfExprNested(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(list : java.util.List<String>){
          val s = "Not a valid java identifier<caret> part in " + (if (list.size() == 1) list.get(0) + " prefix's" else "suffix's"))
        }
      }
    """.trimIndent())
    readAction {
      val enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(myFixture.file, myFixture.editor)
      val concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral)
      assertNotNull(concatenation)
      val args = ArrayList<UExpression?>()
      assertEquals("Not a valid java identifier part in {1, choice, 0#{0} prefix''''s|1#suffix''''s}",
                   JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, myFixture.project))
      assertEquals(2, args.size)
      assertEquals("list.get(0)", args[0]!!.sourcePsi!!.text)
      assertEquals("if (list.size() == 1) 0 else 1", args[1]!!.sourcePsi!!.text)
    }
  }

  @Test
  fun testConcatenationWithIfExprNested1(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(list : java.util.List<String>){
          val s = "Not a valid java identifier part in " + (if (list.size() == 1) list.get(0) + " prefix's" else "su<caret>ffix's"))
        }
      }
    """.trimIndent())
    readAction {
      val enclosingStringLiteral = I18nizeConcatenationQuickFix.getEnclosingLiteralConcatenation(myFixture.file.findElementAt(myFixture.editor.caretModel.offset))

      val concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral)
      assertNotNull(concatenation)
      val args = ArrayList<UExpression?>()
      assertEquals("Not a valid java identifier part in {1, choice, 0#{0} prefix''''s|1#suffix''''s}",
                   JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, myFixture.project))
      assertEquals(2, args.size)
      assertEquals("list.get(0)", args[0]!!.sourcePsi!!.text)
      assertEquals("if (list.size() == 1) 0 else 1", args[1]!!.sourcePsi!!.text)
    }
  }

  @Test
  fun testConcatenationOfLiterals(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("Test.kt", """
      class MyTest {
        fun f(prefix : Boolean){
          val s = "<caret>part in " + "suffix {0} and prefix '{1}'"
        }
      }
    """.trimIndent())
    readAction {
      val enclosingStringLiteral = I18nizeAction.getEnclosingStringLiteral(myFixture.file, myFixture.editor)
      val concatenation = UStringConcatenationsFacade.createFromTopConcatenation(enclosingStringLiteral)
      assertNotNull(concatenation)
      val args = ArrayList<UExpression?>()
      assertEquals("part in suffix {0} and prefix '{1}'",
                   JavaI18nUtil.buildUnescapedFormatString(concatenation!!, args, myFixture.project))
    }
  }

  @Test
  fun testLightAnnotationTypeResolve(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    myFixture.configureByText("Test.kt", """
      annotation class MyMetaAnnotation
      
      @MyMetaAnnotation
      annotation class MyAnnotation

      @MyAnnotation
      class MyClass<caret>
    """.trimIndent())

    readAction {
      val psiClass = myFixture.elementAtCaret.toUElementOfType<UClass>()?.javaPsi
                     ?: error("PsiClass not found")

      val resolvedAnnotationType = psiClass.annotations.firstOrNull()?.resolveAnnotationType()
      assertNull(resolvedAnnotationType)

      assertTrue(MetaAnnotationUtil.isMetaAnnotated(psiClass, listOf("MyMetaAnnotation")))
    }
  }
}