// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.ide.inspection.auto

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class AutoFixTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    (grazie.fixture as CodeInsightTestFixtureImpl).canChangeDocumentDuringHighlighting(true)
    GrazieConfig.update { it.withAutoFix(true) }
    service<GrazieCheckers>().awaitConfiguration()
  }

  @Test
  fun testCyrillicEnglishMix() {
    grazie.fixture.configureByText("a.txt", "")
    grazie.fixture.type("I h\u0430v\u0435 a big cat.")
    grazie.fixture.doHighlighting()
    grazie.fixture.checkResult("I have a big cat.")
  }

  @Test
  fun testNoAutoFixAfterUndo(): Unit = timeoutRunBlocking {
    val englishText = "I have a big cat."
    val mixedText = "I h\u0430v\u0435 a big cat."

    grazie.fixture.configureByText("a.txt", "I h<caret>v\u0435 a big cat.")
    grazie.fixture.type("\u0430")
    leaveTheHighlightedRegionForTheAutoFixToWork()
    grazie.fixture.doHighlighting()
    grazie.fixture.checkResult(englishText)

    edtWriteAction {
      UndoManager.getInstance(grazie.project).undo(FileEditorManager.getInstance(grazie.project).getSelectedEditor(grazie.fixture.file.virtualFile))
    }
    leaveTheHighlightedRegionForTheAutoFixToWork()

    grazie.fixture.checkResult(mixedText)
    grazie.fixture.doHighlighting()
    grazie.fixture.checkResult(mixedText)
  }

  private fun leaveTheHighlightedRegionForTheAutoFixToWork() {
    grazie.fixture.performEditorAction(IdeActions.ACTION_EDITOR_MOVE_LINE_END)
  }
}
