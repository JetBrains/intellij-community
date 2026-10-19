// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor

import com.intellij.codeInsight.navigation.actions.navigateRequest
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.platform.ide.navigation.NavigationOptions
import com.intellij.platform.ide.navigation.NavigationService
import com.intellij.platform.ide.navigation.RequestedEditor
import com.intellij.platform.ide.navigation.requestNavigate
import com.intellij.platform.ide.navigation.impl.performNavigationHistoryAware
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.testFramework.EditorTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.awaitPendingNavigation
import com.intellij.testFramework.executeSomeCoroutineTasksAndDispatchAllInvocationEvents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JSplitPane

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
    awaitPendingNavigation(project)
    myFixture.checkResult("""
      class <caret>AA {}

      class BV extends AA {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_BACK)
    awaitPendingNavigation(project)
    myFixture.checkResult("""
      class AA {}

      class BV extends A<caret>A {}""".trimIndent())
    EditorTestUtil.executeAction(editor, IdeActions.ACTION_GOTO_FORWARD)
    awaitPendingNavigation(project)
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
      val job = coroutineScope.launch {
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
    val job = coroutineScope.launch {
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

  fun testMultipleTargetsRecordSingleBackPlace() {
    withNavigationRequests(isAsync = true) {
      val target1 = myFixture.addFileToProject("target1.txt", "target1").virtualFile
      val target2 = myFixture.addFileToProject("target2.txt", "target2").virtualFile
      myFixture.configureByText("source.txt", "source<caret>")
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      clearDocumentHistory()

      val result = AtomicReference<Boolean>()
      val requests = listOf(createNavigationRequest(target1, 0), createNavigationRequest(target2, 0))
      coroutineScope.launch {
        result.set(project.serviceAsync<NavigationService>().navigate(requests, NavigationOptions.defaultOptions()))
      }

      waitUntil("Navigation did not open both targets") {
        fileEditorManager.isFileOpen(target1) && fileEditorManager.isFileOpen(target2)
      }
      waitUntil("Navigation did not commit source place to history") {
        IdeDocumentHistory.getInstance(project).isBackAvailable()
      }
      waitUntil("Navigation result was not produced") { result.get() != null }
      assertThat(result.get()).isTrue()
      assertThat(IdeDocumentHistory.getInstance(project).getBackPlaces()).hasSize(1)
    }
  }

  fun testEmptyRequestBatchReturnsFalse() {
    val result = AtomicReference<Boolean>()
    coroutineScope.launch {
      result.set(
        project.serviceAsync<NavigationService>().navigate(
          emptyList<NavigationRequest>(),
          NavigationOptions.defaultOptions(),
        )
      )
    }

    waitUntil("Navigation result was not produced") { result.get() != null }
    assertThat(result.get()).isFalse()
  }

  fun testNavigationFutureCancellationStopsRequestComputation() {
    withNavigationRequests(isAsync = true) {
      val entered = CompletableDeferred<Unit>()
      val stopped = CompletableDeferred<Unit>()
      val result = requestNavigate(project, dataContext = DataContext.EMPTY_CONTEXT) {
        try {
          entered.complete(Unit)
          awaitCancellation()
        }
        finally {
          stopped.complete(Unit)
        }
      }

      try {
        waitUntil("Navigation did not start computing its requests") { entered.isCompleted }
        assertThat(result.cancel(false)).isTrue()
        waitUntil("Future cancellation did not stop request computation") { stopped.isCompleted }
        assertThat(result.isCancelled).isTrue()
      }
      finally {
        result.cancel(false)
      }
    }
  }

  fun testNavigationRequestFailureCompletesFutureExceptionally() {
    withNavigationRequests(isAsync = true) {
      val failure = IllegalStateException("Cannot resolve the navigation target")
      val result = requestNavigate(project, dataContext = DataContext.EMPTY_CONTEXT) {
        throw failure
      }
      val error = result.handle { _, error -> error }

      waitUntil("Navigation failure did not complete the future") { error.isDone }
      assertThat(result.isCompletedExceptionally).isTrue()
      assertThat(error.join()).isInstanceOf(IllegalStateException::class.java).hasMessage(failure.message)
    }
  }

  fun testNavigationWhichOpenedNothingLetsAnOlderPreparationToApply() {
    withNavigationRequests(isAsync = true) {
      val directory = myFixture.tempDirFixture.findOrCreateDir("directory")
      val target = myFixture.addFileToProject("target.txt", "target").virtualFile
      myFixture.configureByText("source.txt", "source<caret>")
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      clearDocumentHistory()

      val preparing = CompletableDeferred<Unit>()
      val releasePreparation = CompletableDeferred<Unit>()
      val targetRequest = createNavigationRequest(target, 0)
      val scope = coroutineScope
      val olderResult = AtomicReference<Boolean>()
      scope.launch {
        olderResult.set(
          project.serviceAsync<NavigationService>().navigateRequests(NavigationOptions.defaultOptions()) {
            preparing.complete(Unit)
            releasePreparation.await()
            listOf(targetRequest)
          }
        )
      }
      waitUntil("Older navigation did not start resolving its targets") { preparing.isCompleted }

      // a directory request under `sourceNavigationOnly` takes the turn and then opens nothing
      val sourceOnlyResult = AtomicReference<Boolean>()
      scope.launch {
        sourceOnlyResult.set(
          project.serviceAsync<NavigationService>().navigate(
            listOf(createDirectoryNavigationRequest(directory)),
            NavigationOptions.defaultOptions().sourceNavigationOnly(true),
          )
        )
      }
      waitUntil("Source-only navigation result was not produced") { sourceOnlyResult.get() != null }
      assertThat(sourceOnlyResult.get()).isFalse()

      releasePreparation.complete(Unit)
      waitUntil("Older navigation did not open its target") { fileEditorManager.isFileOpen(target) }
      waitUntil("Older navigation result was not produced") { olderResult.get() != null }
      assertThat(olderResult.get()).isTrue()
      assertThat(IdeDocumentHistory.getInstance(project).getBackPlaces()).hasSize(1)
    }
  }

  fun testBatchOpensFirstHandledSourceInRightSplit() {
    withNavigationRequests(isAsync = true) {
      val directory = myFixture.tempDirFixture.findOrCreateDir("directory")
      val target1 = myFixture.addFileToProject("target1.txt", "target1").virtualFile
      val target2 = myFixture.addFileToProject("target2.txt", "target2").virtualFile
      myFixture.configureByText("source.txt", "source<caret>")
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val result = AtomicReference<Boolean>()
      val requests = listOf(
        createDirectoryNavigationRequest(directory),
        createNavigationRequest(target1, 0),
        createNavigationRequest(target2, 0),
      )

      coroutineScope.launch {
        result.set(
          project.serviceAsync<NavigationService>().navigate(
            requests,
            NavigationOptions.defaultOptions().openInRightSplit(true),
          )
        )
      }

      waitUntil("Navigation did not open both source targets") {
        fileEditorManager.isFileOpen(target1) && fileEditorManager.isFileOpen(target2)
      }
      waitUntil("Navigation result was not produced") { result.get() != null }
      assertThat(result.get()).isTrue()
      assertThat(fileEditorManager.windowSplitCount).isEqualTo(2)
      assertThat(fileEditorManager.currentWindow!!.fileList).contains(target1, target2)
    }
  }

  fun testBatchOpensAlreadyOpenSourcesInRightSplit() {
    withNavigationRequests(isAsync = true) {
      val target1 = myFixture.addFileToProject("target1.txt", "target1").virtualFile
      val target2 = myFixture.addFileToProject("target2.txt", "target2").virtualFile
      myFixture.configureByText("source.txt", "source<caret>")
      myFixture.openFileInEditor(target1)
      myFixture.openFileInEditor(target2)
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val initialWindow = fileEditorManager.currentWindow!!
      val result = AtomicReference<Boolean>()
      val requests = listOf(
        createNavigationRequest(target1, 0),
        createNavigationRequest(target2, 0),
      )

      coroutineScope.launch {
        result.set(
          project.serviceAsync<NavigationService>().navigate(
            requests,
            NavigationOptions.defaultOptions().openInRightSplit(true),
          )
        )
      }

      waitUntil("Navigation result was not produced") { result.get() != null }
      assertThat(result.get()).isTrue()
      assertThat(fileEditorManager.windowSplitCount).isEqualTo(2)
      val splitWindow = fileEditorManager.windows.single { it !== initialWindow }
      assertThat(splitWindow.fileList).contains(target1, target2)
    }
  }

  fun testBatchKeepsTheFirstOpenFileWithForbiddenSplitOutOfTheRightSplit() {
    doTestBatchWithFileWithForbiddenSplit(forbiddenFirst = true, forbiddenIsOpen = true)
  }

  fun testBatchKeepsTheLastOpenFileWithForbiddenSplitOutOfTheRightSplit() {
    doTestBatchWithFileWithForbiddenSplit(forbiddenFirst = false, forbiddenIsOpen = true)
  }

  fun testBatchKeepsTheLastOpenFileWithForbiddenSplitOutOfTheRightSplitWhenOpenInInactiveSplitterIsOff() {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting(FileEditorManagerImpl.EDITOR_OPEN_INACTIVE_SPLITTER, false, testRootDisposable)
    doTestBatchWithFileWithForbiddenSplit(forbiddenFirst = false, forbiddenIsOpen = true)
  }

  fun testBatchKeepsTheFirstOpenFileWithForbiddenSplitOutOfTheRightSplitWhenOpenInInactiveSplitterIsOff() {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting(FileEditorManagerImpl.EDITOR_OPEN_INACTIVE_SPLITTER, false, testRootDisposable)
    doTestBatchWithFileWithForbiddenSplit(forbiddenFirst = true, forbiddenIsOpen = true)
  }

  fun testBatchOpensTheFirstClosedFileWithForbiddenSplitInTheRightSplit() {
    doTestBatchWithFileWithForbiddenSplit(forbiddenFirst = true, forbiddenIsOpen = false)
  }

  fun testBatchOpensTheLastClosedFileWithForbiddenSplitInTheRightSplit() {
    doTestBatchWithFileWithForbiddenSplit(forbiddenFirst = false, forbiddenIsOpen = false)
  }

  private fun doTestBatchWithFileWithForbiddenSplit(forbiddenFirst: Boolean, forbiddenIsOpen: Boolean) {
    withNavigationRequests(isAsync = true) {
      val forbidden = myFixture.addFileToProject("forbidden.txt", "forbidden").virtualFile
      forbidden.putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
      val target1 = myFixture.addFileToProject("target1.txt", "target1").virtualFile
      val target2 = myFixture.addFileToProject("target2.txt", "target2").virtualFile
      if (forbiddenIsOpen) {
        myFixture.openFileInEditor(forbidden)
      }
      myFixture.openFileInEditor(target1)
      myFixture.openFileInEditor(target2)
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val initialWindow = fileEditorManager.currentWindow!!
      val initialSelectedFile = initialWindow.selectedFile
      val forbiddenComposite = initialWindow.getComposite(forbidden)
      val result = AtomicReference<Boolean>()
      val targetRequests = listOf(
        createNavigationRequest(target1, 0),
        createNavigationRequest(target2, 0),
      )
      val forbiddenRequest = createNavigationRequest(forbidden, 0)
      val requests = if (forbiddenFirst) listOf(forbiddenRequest) + targetRequests else targetRequests + forbiddenRequest

      coroutineScope.launch {
        result.set(
          project.serviceAsync<NavigationService>().navigate(
            requests,
            NavigationOptions.defaultOptions().openInRightSplit(true),
          )
        )
      }

      waitUntil("Navigation result was not produced") { result.get() != null }
      assertThat(result.get()).isTrue()
      assertThat(fileEditorManager.windowSplitCount).isEqualTo(2)
      val splitWindow = fileEditorManager.windows.single { it !== initialWindow }
      assertThat(fileEditorManager.currentWindow).isSameAs(splitWindow)
      if (forbiddenIsOpen) {
        assertThat(splitWindow.fileList).containsExactlyInAnyOrder(target1, target2)
        assertThat(splitWindow.selectedFile).isSameAs(target2)
        assertThat(initialWindow.getComposite(forbidden)).isSameAs(forbiddenComposite)
      }
      else {
        assertThat(splitWindow.fileList).containsExactlyInAnyOrder(forbidden, target1, target2)
        assertThat(splitWindow.selectedFile).isSameAs(if (forbiddenFirst) target2 else forbidden)
        assertThat(initialWindow.fileList).doesNotContain(forbidden)
      }
      assertThat(initialWindow.selectedFile).isSameAs(initialSelectedFile)
      assertThat(forbidden.isValid).isTrue()
    }
  }

  fun testBatchOfOneOpenFileWithForbiddenSplitSelectsItInItsWindow() {
    doTestBatchOfOpenFilesWithForbiddenSplitOnly(count = 1, twoWindowsAway = false)
  }

  fun testBatchOfTwoOpenFilesWithForbiddenSplitSelectsThemInTheirWindow() {
    doTestBatchOfOpenFilesWithForbiddenSplitOnly(count = 2, twoWindowsAway = false)
  }

  fun testBatchOfTwoOpenFilesWithForbiddenSplitTwoWindowsAwaySelectsThemInTheirWindowWhenOpenInInactiveSplitterIsOff() {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting(FileEditorManagerImpl.EDITOR_OPEN_INACTIVE_SPLITTER, false, testRootDisposable)
    doTestBatchOfOpenFilesWithForbiddenSplitOnly(count = 2, twoWindowsAway = true)
  }

  /**
   * The right split selects a file in a sibling window also without the refusal.
   * So [twoWindowsAway] puts the current window two splits away from the files.
   */
  private fun doTestBatchOfOpenFilesWithForbiddenSplitOnly(count: Int, twoWindowsAway: Boolean) {
    withNavigationRequests(isAsync = true) {
      val forbiddenFiles = (1..count).map { index ->
        val file = myFixture.addFileToProject("forbidden$index.txt", "forbidden$index").virtualFile
        file.putUserData(FileEditorManagerKeys.FORBID_TAB_SPLIT, true)
        myFixture.openFileInEditor(file)
        file
      }
      val regular = myFixture.addFileToProject("regular.txt", "regular").virtualFile
      myFixture.openFileInEditor(regular)
      val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
      val forbiddenWindow = fileEditorManager.currentWindow!!
      val forbiddenComposites = forbiddenFiles.map { forbiddenWindow.getComposite(it) }
      if (twoWindowsAway) {
        val secondaryWindow = forbiddenWindow.split(JSplitPane.HORIZONTAL_SPLIT, true, regular, true)!!
        val tertiaryWindow = secondaryWindow.split(JSplitPane.HORIZONTAL_SPLIT, true, regular, true)!!
        assertThat(fileEditorManager.currentWindow).isSameAs(tertiaryWindow)
      }
      val windowCount = fileEditorManager.windowSplitCount
      val result = AtomicReference<Boolean>()

      coroutineScope.launch {
        result.set(
          project.serviceAsync<NavigationService>().navigate(
            forbiddenFiles.map { createNavigationRequest(it, 0) },
            NavigationOptions.defaultOptions().openInRightSplit(true),
          )
        )
      }

      waitUntil("Navigation result was not produced") { result.get() != null }
      assertThat(result.get()).isTrue()
      assertThat(fileEditorManager.windowSplitCount).isEqualTo(windowCount)
      assertThat(forbiddenFiles.map { forbiddenWindow.getComposite(it) }).containsExactlyElementsOf(forbiddenComposites)
      assertThat(forbiddenWindow.selectedFile).isSameAs(forbiddenFiles.last())
      for (window in fileEditorManager.windows.filter { it !== forbiddenWindow }) {
        assertThat(window.fileList).containsExactly(regular)
      }
    }
  }

  /**
   * A navigatable which navigates by itself cannot be told which editor to use, so [RequestedEditor.None] reaches it
   * as the suppression of the ambient one; this is what keeps such a navigation out of the editor a diff viewer publishes.
   */
  fun testRequestedEditorNoneReachesANavigatableWhichNavigatesByItself() {
    withNavigationRequests(isAsync = true) {
      myFixture.configureByText("${getTestName(false)}.txt", "source<caret>")
      val scope = coroutineScope
      val suppressed = AtomicReference<Boolean>()
      val navigatable = object : Navigatable {
        override fun navigate(requestFocus: Boolean) {
          suppressed.set(isContextEditorSuppressed)
        }

        override fun canNavigate(): Boolean = true

        override fun canNavigateToSource(): Boolean = false
      }

      scope.launch {
        project.serviceAsync<NavigationService>().navigate(
          listOf(navigatable),
          NavigationOptions.defaultOptions().requestedEditor(RequestedEditor.None),
        )
      }
      waitUntil("Navigation with no requested editor did not reach the navigatable") { suppressed.get() != null }
      assertThat(suppressed.get()).isTrue()

      suppressed.set(null)
      scope.launch {
        project.serviceAsync<NavigationService>().navigate(listOf(navigatable), NavigationOptions.defaultOptions())
      }
      waitUntil("Navigation did not reach the navigatable") { suppressed.get() != null }
      assertThat(suppressed.get()).isFalse()
    }
  }

  fun testNavigateRequestIsDeferredFromWriteAction() {
    withNavigationRequests(isAsync = false) {
      withWriteLockFreeNavigation {
        myFixture.configureByText("${getTestName(false)}.txt", "target\n\nsource<caret>")
        val file = myFixture.file.virtualFile
        val sourceOffset = editor.caretModel.offset
        val targetOffset = editor.document.text.indexOf("target")
        val request = createNavigationRequest(file, targetOffset)

        ApplicationManager.getApplication().runWriteAction {
          navigateRequest(project, request)
          assertThat(editor.caretModel.offset).isEqualTo(sourceOffset)
        }

        waitUntil("Registry fallback did not navigate after leaving the write action") {
          editor.caretModel.offset == targetOffset
        }
      }
    }
  }

  @Suppress("UsagesOfObsoleteApi")
  private val coroutineScope: CoroutineScope
    get() = (project as ComponentManagerEx).getCoroutineScope()

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
    navigateRequest(project, createNavigationRequest(file, offset))
  }

  private fun createNavigationRequest(file: VirtualFile, offset: Int): NavigationRequest {
    return ApplicationManager.getApplication().executeOnPooledThread<NavigationRequest?> {
      ReadAction.computeBlocking<NavigationRequest?, RuntimeException> {
        NavigationRequest.sourceNavigationRequest(project, file, offset)
      }
    }.get() ?: error("Cannot create navigation request for ${file.path}:$offset")
  }

  private fun createDirectoryNavigationRequest(directory: VirtualFile): NavigationRequest {
    return ApplicationManager.getApplication().executeOnPooledThread<NavigationRequest?> {
      ReadAction.computeBlocking<NavigationRequest?, RuntimeException> {
        val psiDirectory = PsiManager.getInstance(project).findDirectory(directory)
                           ?: error("Cannot find PSI directory for ${directory.path}")
        NavigationRequest.directoryNavigationRequest(psiDirectory)
      }
    }.get() ?: error("Cannot create directory navigation request for ${directory.path}")
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

  private inline fun withWriteLockFreeNavigation(crossinline action: () -> Unit) {
    val registryValue = Registry.get("ide.navigation.write.lock.free")
    val oldValue = registryValue.asBoolean()
    registryValue.setValue(true)
    try {
      action()
    }
    finally {
      registryValue.setValue(oldValue)
    }
  }
}
