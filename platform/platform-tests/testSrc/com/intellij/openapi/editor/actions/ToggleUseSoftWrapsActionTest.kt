// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.actions

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.openapi.editor.impl.softwrap.SoftWrapAppliancePlaces
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
class ToggleUseSoftWrapsActionTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  private val globalAction by lazy { ToggleUseSoftWrapsToolbarAction(SoftWrapAppliancePlaces.MAIN_EDITOR) }

  @Test
  fun testGlobalActionShowsGlobalSettingForLocalClient(): Unit = fixture.runTest {
    initText("text")
    val settings = EditorSettingsExternalizable.getInstance()
    val wasUseSoftWraps = settings.isUseSoftWraps(SoftWrapAppliancePlaces.MAIN_EDITOR)
    settings.setUseSoftWraps(true, SoftWrapAppliancePlaces.MAIN_EDITOR)
    try {
      val event = createEvent(editor)
      ActionUtil.updateAction(globalAction, event)
      assertTrue(event.presentation.isEnabledAndVisible)
      assertTrue(Toggleable.isSelected(event.presentation))
    }
    finally {
      settings.setUseSoftWraps(wasUseSoftWraps, SoftWrapAppliancePlaces.MAIN_EDITOR)
    }
  }

  @Test
  fun testGlobalActionIsHiddenForClientWithoutSession(): Unit = fixture.runTest {
    initText("text")
    val event = createEvent(editor)
    withClientWithoutSession {
      ActionUtil.updateAction(globalAction, event)
    }
    assertFalse(event.presentation.isEnabledAndVisible)
  }

  @Test
  fun testGlobalActionIsNotSelectedForClientWithoutSession(): Unit = fixture.runTest {
    initText("text")
    val event = createEvent(editor)
    assertFalse(withClientWithoutSession { globalAction.isSelected(event) })
  }

  private fun createEvent(editor: Editor): AnActionEvent {
    return TestActionEvent.createTestEvent(globalAction, SimpleDataContext.getSimpleContext(CommonDataKeys.EDITOR, editor))
  }

  private fun <T> withClientWithoutSession(action: () -> T): T {
    return ClientId.withExplicitClientId(ClientId("client-without-session"), action)
  }
}
