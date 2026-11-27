// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandExecutor
import com.intellij.modcompletion.CommonCompletionItem
import com.intellij.modcompletion.ModCompletionItem
import com.intellij.openapi.command.CommandProcessor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks [CommonCompletionItem.perform] without an update in a file with an injected fragment.
 */
@TestApplication
class CommonCompletionItemInjectedTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21)
  }

  private val codeInsightFixture = lightJavaCodeInsightFixture(projectFixture)
  private val myFixture get() = codeInsightFixture.get()

  private val fastItem = CommonCompletionItem("\"q\"")

  // A no-op updater makes the item use ModCommand.psiUpdate
  private val psiUpdateItem = fastItem.withAdditionalUpdater { _, _ -> }

  private val insertionContexts = listOf(
    ModCompletionItem.DEFAULT_INSERTION_CONTEXT,
    ModCompletionItem.InsertionContext(ModCompletionItem.InsertionMode.INSERT, ';'),
    ModCompletionItem.InsertionContext(ModCompletionItem.InsertionMode.OVERWRITE, '\t'),
  )

  private fun configure() {
    myFixture.configureByText(JavaFileType.INSTANCE, """
      class A {
        @org.intellij.lang.annotations.Language("JAVA") String s = "class B { String y = <caret>q; }";
      }
      """.trimIndent())
  }

  private val hostEditor get() = InjectedLanguageEditorUtil.getTopLevelEditor(myFixture.editor)

  private fun hostContext(): ActionContext {
    val manager = InjectedLanguageManager.getInstance(myFixture.project)
    val context = ActionContext.from(hostEditor, manager.getTopLevelFile(myFixture.file))
    assertFalse(manager.isInjectedFragment(context.file()))
    return context
  }

  private fun injectedContext(): ActionContext {
    val context = hostContext().mapToInjected()
    assertTrue(InjectedLanguageManager.getInstance(myFixture.project).isInjectedFragment(context.file()))
    return context
  }

  private fun perform(context: ActionContext, command: ModCommand) {
    CommandProcessor.getInstance().executeCommand(myFixture.project, {
      ModCommandExecutor.getInstance().executeInteractively(context, command, hostEditor)
    }, "Test", null)
  }

  private fun checkHostResult(expected: String) {
    val document = hostEditor.document
    val caret = hostEditor.caretModel.offset
    assertEquals(expected, document.text.substring(0, caret) + "<caret>" + document.text.substring(caret))
  }

  @Test
  fun testHostContextMatchesPsiUpdate(): Unit = runInEdtAndWait {
    configure()
    val context = hostContext()
    for (insertionContext in insertionContexts) {
      assertEquals(psiUpdateItem.perform(context, insertionContext), fastItem.perform(context, insertionContext), insertionContext.toString())
    }
    perform(context, fastItem.perform(context, ModCompletionItem.DEFAULT_INSERTION_CONTEXT))
    checkHostResult("""
      class A {
        @org.intellij.lang.annotations.Language("JAVA") String s = "class B { String y = "q"<caret>q; }";
      }
      """.trimIndent())
  }

  @Test
  fun testInjectedContextMatchesPsiUpdate(): Unit = runInEdtAndWait {
    configure()
    val context = injectedContext()
    for (insertionContext in insertionContexts) {
      assertEquals(psiUpdateItem.perform(context, insertionContext), fastItem.perform(context, insertionContext), insertionContext.toString())
    }
    perform(context, fastItem.perform(context, ModCompletionItem.DEFAULT_INSERTION_CONTEXT))
    checkHostResult("""
      class A {
        @org.intellij.lang.annotations.Language("JAVA") String s = "class B { String y = \"q\"<caret>q; }";
      }
      """.trimIndent())
  }
}
