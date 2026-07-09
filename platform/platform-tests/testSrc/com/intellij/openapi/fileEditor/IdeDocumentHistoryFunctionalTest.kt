// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor

import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.impl.performNavigationHistoryAware
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import com.intellij.util.OpenSourceUtil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

internal class IdeDocumentHistoryFunctionalTest : HeavyFileEditorManagerTestCase() {
  fun testNavigateBetweenEditLocations() {
    myFixture.configureByText("${getTestName(false)}.txt", """
      <caret>line1



      line2



      line3""".trimIndent())
    myFixture.type(' ')
    moveCaret4LinesDown()
    myFixture.type(' ')
    moveCaret4LinesDown()

    myFixture.checkResult("""
       line1



      l ine2



      li<caret>ne3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
       line1



      l <caret>ine2



      line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
       <caret>line1



      l ine2



      line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
       line1



      l <caret>ine2



      line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
       line1



      l <caret>ine2



      line3""".trimIndent())
  }

  fun testNavigateBetweenEditLocationsWithMultiCaret() {
    myFixture.configureByText("${getTestName(false)}.txt", """
      <caret>li<caret>ne1
      -------
      -------
      -------
      line2
      -------
      -------
      -------
      longer_line3""".trimIndent())
    myFixture.type("AAA")
    moveCaret4LinesDown()
    myFixture.type("BBB")
    moveCaret4LinesDown()

    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBBe2BBB
      -------
      -------
      -------
      longer<caret>_line<caret>3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBB<caret>e2BBB<caret>
      -------
      -------
      -------
      longer_line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_LAST_CHANGE)
    myFixture.checkResult("""
      AAA<caret>liAAA<caret>ne1
      -------
      -------
      -------
      linBBBe2BBB
      -------
      -------
      -------
      longer_line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBB<caret>e2BBB<caret>
      -------
      -------
      -------
      longer_line3""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_NEXT_CHANGE)
    myFixture.checkResult("""
      AAAliAAAne1
      -------
      -------
      -------
      linBBB<caret>e2BBB<caret>
      -------
      -------
      -------
      longer_line3""".trimIndent())
  }

  fun testForwardToANearPlace() {
    myFixture.configureByText("${getTestName(false)}.java", """
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_DECLARATION)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_BACK)
    myFixture.checkResult("""
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_FORWARD)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
  }

  fun testAsyncSamePlaceNavigationDoesNotCreateBackPlace() {
    withNavigationRequests(isAsync = true) {
      myFixture.configureByText("${getTestName(false)}.txt", """
        target

        source<caret>
      """.trimIndent())
      val file = myFixture.file.virtualFile
      val sourceOffset = editor.caretModel.offset
      val targetOffset = editor.document.text.indexOf("target")
      clearDocumentHistory()

      navigateToSource(file, sourceOffset)
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

      assertThat(IdeDocumentHistory.getInstance(project).getBackPlaces()).isEmpty()

      navigateToSourceAndWait(file, targetOffset, "Navigation did not move caret after same-place navigation") {
        editor.caretModel.offset == targetOffset
      }
      waitUntil("Navigation did not commit source place to history after same-place navigation") {
        IdeDocumentHistory.getInstance(project).isBackAvailable()
      }
    }
  }

  fun testBlockingNavigationInsideCommandRecordsSingleBackPlace() {
    withNavigationRequests(isAsync = false) {
      myFixture.configureByText("${getTestName(false)}.txt", """
        target

        source<caret>""".trimIndent())
      val file = myFixture.file.virtualFile
      val targetOffset = editor.document.text.indexOf("target")
      clearDocumentHistory()

      // GTD-style: the navigation runs inside a command which is explicitly marked as navigation
      CommandProcessor.getInstance().executeCommand(project, {
        navigateToSource(file, targetOffset)
      }, "", null)

      waitUntil("Navigation did not move caret to target") {
        editor.caretModel.offset == targetOffset
      }
      waitUntil("Navigation did not commit source place to history") {
        IdeDocumentHistory.getInstance(project).isBackAvailable()
      }
      assertThat(IdeDocumentHistory.getInstance(project).getBackPlaces()).hasSize(1)
    }
  }

  fun testHistoryRecordingSurvivesCancelledNavigation() {
    withNavigationRequests(isAsync = true) {
      myFixture.configureByText("${getTestName(false)}.txt", """
        target

        source<caret>""".trimIndent())
      val file = myFixture.file.virtualFile
      val targetOffset = editor.document.text.indexOf("target")
      clearDocumentHistory()

      val entered = CompletableDeferred<Unit>()
      @Suppress("UsagesOfObsoleteApi")
      val scope = (project as ComponentManagerEx).getCoroutineScope()
      val job = scope.launch {
        performNavigationHistoryAware(project) {
          entered.complete(Unit)
          awaitCancellation()
        }
      }
      waitUntil("History-aware action did not start") { entered.isCompleted }
      job.cancel()
      waitUntil("Cancelled navigation did not complete") { job.isCompleted }
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)

      assertThat(IdeDocumentHistory.getInstance(project).getBackPlaces()).isEmpty()

      navigateToSourceAndWait(file, targetOffset, "Navigation did not move caret to target after a cancelled navigation") {
        editor.caretModel.offset == targetOffset
      }
      waitUntil("History was not recorded after a cancelled navigation") {
        IdeDocumentHistory.getInstance(project).isBackAvailable()
      }
    }
  }

  fun testUnrelatedCommandIsRecordedWhileNavigationIsSuspended() {
    myFixture.configureByText("${getTestName(false)}.txt", """
      target

      source<caret>""".trimIndent())
    val targetOffset = editor.document.text.indexOf("target")
    clearDocumentHistory()

    val entered = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()
    @Suppress("UsagesOfObsoleteApi")
    val scope = (project as ComponentManagerEx).getCoroutineScope()
    val job = scope.launch {
      performNavigationHistoryAware(project) {
        entered.complete(Unit)
        release.await()
      }
    }
    waitUntil("History-aware action did not start") { entered.isCompleted }

    val history = IdeDocumentHistory.getInstance(project)
    CommandProcessor.getInstance().executeCommand(project, {
      history.includeCurrentCommandAsNavigation()
      editor.caretModel.moveToOffset(targetOffset)
      history.setCurrentCommandHasMoves()
    }, "", null)

    assertThat(history.isBackAvailable).isTrue()
    release.complete(Unit)
    waitUntil("History-aware action did not complete") { job.isCompleted }
    assertThat(history.getBackPlaces()).hasSize(1)
  }

  fun testOpenSourceUtilFallbackIsDeferredFromWriteAction() {
    withNavigationRequests(isAsync = false) {
      myFixture.configureByText("${getTestName(false)}.txt", "target\n\nsource<caret>")
      val file = myFixture.file.virtualFile
      val sourceOffset = editor.caretModel.offset
      val targetOffset = editor.document.text.indexOf("target")
      val descriptor = OpenFileDescriptor(project, file, targetOffset)

      ApplicationManager.getApplication().runWriteAction {
        OpenSourceUtil.navigate(true, false, descriptor)
        assertThat(editor.caretModel.offset).isEqualTo(sourceOffset)
      }

      waitUntil("OpenSourceUtil fallback did not navigate after leaving the write action") {
        editor.caretModel.offset == targetOffset
      }
    }
  }

  private fun moveCaret4LinesDown() {
    for (i in 0..3) {
      EditorTestUtil.executeAction(editor, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    }
  }

  private fun navigateToSourceAndWait(file: VirtualFile, offset: Int, message: String, condition: () -> Boolean) {
    navigateToSource(file, offset)
    waitUntil(message, condition)
  }

  private fun navigateToSource(file: VirtualFile, offset: Int) {
    // bg-thread is required
    val request = ApplicationManager.getApplication().executeOnPooledThread<NavigationRequest?> {
      ReadAction.computeBlocking<NavigationRequest?, RuntimeException> {
        NavigationRequest.sourceNavigationRequest(project, file, offset)
      }
    }.get() ?: error("Cannot create navigation request for ${file.path}:$offset")
    navigateRequest(project, request)
  }

  private fun waitUntil(message: String, condition: () -> Boolean) {
    PlatformTestUtil.waitWithEventsDispatching(message, {
      executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
      condition()
    }, 10)
  }

  private fun clearDocumentHistory() {
    executeSomeCoroutineTasksAndDispatchAllInvocationEvents(project)
    IdeDocumentHistory.getInstance(project).clearHistory()
  }

  private fun withNavigationRequests(isAsync: Boolean, action: () -> Unit) {
    val registryValue = Registry.get("ide.navigation.requests")
    val oldValue = registryValue.asBoolean()
    registryValue.setValue(isAsync)
    try {
      action()
    }
    finally {
      registryValue.setValue(oldValue)
    }
  }
}
