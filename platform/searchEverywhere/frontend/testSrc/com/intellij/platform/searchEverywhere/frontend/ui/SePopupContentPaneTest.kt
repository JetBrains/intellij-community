// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.UI
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.frontend.SeTabInfo
import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionsTab
import com.intellij.platform.searchEverywhere.frontend.toolbar.SeToolbarSearchField
import com.intellij.platform.searchEverywhere.frontend.vm.SeDummyTabVm
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.swing.JTextField

@TestApplication
internal class SePopupContentPaneTest {
  @Test
  fun escapeClosesPopupBeforeModelInitialization(@TestDisposable disposable: Disposable) {
    checkEscapeClosesIdlePopup(externalField = false, disposable)
  }

  @Test
  fun escapeClosesToolbarPopupBeforeModelInitialization(@TestDisposable disposable: Disposable) {
    checkEscapeClosesIdlePopup(externalField = true, disposable)
  }

  private fun checkEscapeClosesIdlePopup(externalField: Boolean, disposable: Disposable): Unit = timeoutRunBlocking {
    val popupScope = childScope("SePopupContentPaneTest")
    try {
      val popup = withContext(Dispatchers.UI) {
        val field = if (externalField) SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR) else null
        lateinit var popup: JBPopup
        val content = SePopupContentPane(
          project = null,
          resizePopupHandler = {},
          closePopupHandler = { popup.cancel() },
          searchStatePublisher = SeSearchStatePublisher(),
          coroutineScope = popupScope,
          initialTabs = listOf(SeDummyTabVm(SeActionsTab.ID, SeTabInfo(SeActionsTab.PRIORITY, SeActionsTab.NAME))),
          selectedTabId = SeActionsTab.ID,
          initialSearchText = null,
          selectSearchText = true,
          initPopupExtendedSize = null,
          initialSelectionState = null,
          externalTextField = field,
        )
        popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, content.preferableFocusedComponent)
          .setCancelKeyEnabled(false)
          .createPopup()
        Disposer.register(disposable, popup)
        Disposer.register(popup, content)
        Disposer.register(popup) { popupScope.cancel() }

        (content.preferableFocusedComponent as JTextField).text = "Find Action"
        val escapeShortcuts = ActionManager.getInstance().getAction("EditorEscape").shortcutSet.shortcuts.toSet()
        val escapeAction = ActionUtil.getActions(field ?: content).single { action ->
          action.shortcutSet.shortcuts.any { it in escapeShortcuts }
        }
        val event = AnActionEvent.createEvent(escapeAction, DataContext.EMPTY_CONTEXT, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
        escapeAction.actionPerformed(event)
        popup
      }

      assertTrue(popup.isDisposed)
      assertFalse(popupScope.isActive)
    }
    finally {
      popupScope.cancel()
    }
  }
}
