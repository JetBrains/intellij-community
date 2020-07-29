// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.kotlin.quickfix

import com.intellij.codeInspection.i18n.I18nizeAction
import com.intellij.codeInspection.i18n.JavaI18nUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import junit.framework.TestCase
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.expressions.UStringConcatenationsFacade.Companion.createFromTopConcatenation
import org.junit.Assert
import java.util.*

private const val i18nizedExpr = "i18nizedExpr"

/**
 * Analogical Java tests: [com.intellij.java.codeInsight.daemon.quickFix.I18nizeTest]
 */
class KtI18nizeTest : LightJavaCodeInsightFixtureTestCase() {

  private fun doTest(before: String, expected: String? = null, i18nized: String = i18nizedExpr) {
    myFixture.configureByText("Test.kt", before)
    val action = I18nizeAction()
    val dataContext = DataManager.getInstance().getDataContext(editor.component)
    val event = AnActionEvent.createFromAnAction(action, null, "place", dataContext)
    action.update(event)
    val handler = I18nizeAction.getHandler(event)
    handler?.checkApplicability(file, editor)
    TestCase.assertEquals(expected != null, event.presentation.isEnabled)
    if (expected != null) {
      val literalExpression = I18nizeAction.getEnclosingStringLiteral(file, editor)
      WriteCommandAction.runWriteCommandAction(myFixture.project) {
        assertNotNull(handler)
        handler!!.performI18nization(file,
                                     editor,
                                     literalExpression,
                                     emptyList(),
                                     "key1",
                                     "value1",
                                     i18nized,
                                     emptyArray(),
                                     JavaI18nUtil.DEFAULT_PROPERTY_CREATION_HANDLER)
      }
      myFixture.checkResult(expected)
    }
  }

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
                        JavaI18nUtil.buildUnescapedFormatString(concatenation, args, project))
    assertSize(1, args)
    assertEquals("if (prefix) 0 else 1", args[0]!!.sourcePsi!!.text)
  }
}
