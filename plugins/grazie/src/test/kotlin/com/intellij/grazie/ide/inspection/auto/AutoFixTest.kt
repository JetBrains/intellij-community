// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.ide.inspection.auto

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl

class AutoFixTest : GrazieTestBase() {

  override fun setUp() {
    super.setUp()
    (myFixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    GrazieConfig.update { it.withAutoFix(true) }
    service<GrazieCheckers>().awaitConfiguration()
  }

  fun testCyrillicEnglishMix() {
    myFixture.configureByText("a.txt", "")
    myFixture.type("I h\u0430v\u0435 a big cat.")
    myFixture.doHighlighting()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    myFixture.checkResult("I have a big cat.")
  }

  fun testNoAutoFixAfterUndo() {
    val englishText = "I have a big cat."
    val mixedText = "I h\u0430v\u0435 a big cat."

    myFixture.configureByText("a.txt", "I h<caret>v\u0435 a big cat.")
    myFixture.type("\u0430")
    leaveTheHighlightedRegionForTheAutoFixToWork()
    myFixture.doHighlighting()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    myFixture.checkResult(englishText)

    ApplicationManager.getApplication().invokeAndWait {
      ApplicationManager.getApplication().runWriteAction {
        UndoManager.getInstance(project).undo(FileEditorManager.getInstance(project).getSelectedEditor(myFixture.file.virtualFile))
      }
    }
    leaveTheHighlightedRegionForTheAutoFixToWork()

    myFixture.checkResult(mixedText)
    myFixture.doHighlighting()
    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    myFixture.checkResult(mixedText)
  }

  private fun leaveTheHighlightedRegionForTheAutoFixToWork() {
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
  }
}
