// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.toolbar

import com.intellij.accessibility.TextFieldWithListAccessibleContext
import com.intellij.ide.DataManager
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.UI
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Disposer
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.frontend.SeTabInfo
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.ui.SePopupContentPane
import com.intellij.platform.searchEverywhere.data.SeDataKeys
import com.intellij.platform.searchEverywhere.frontend.vm.SeDummyTabVm
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import javax.swing.JPanel

/**
 * Checks the binding between [SeToolbarSearchField] and [SePopupContentPane] when the toolbar field is the search field.
 */
@TestApplication
internal class SeToolbarSearchFieldTest {
  @Test
  @Timeout(30)
  fun popupContentRegistersItsListenersOnTheField(): Unit = withPopupScope { scope ->
    val field = SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR)
    val keyListeners = field.keyListeners.size
    val focusListeners = field.focusListeners.size

    val content = createPopupContent(field, scope)
    assertEquals(keyListeners + 1, field.keyListeners.size)
    assertEquals(focusListeners + 1, field.focusListeners.size)

    Disposer.dispose(content)
    assertEquals(keyListeners, field.keyListeners.size)
    assertEquals(focusListeners, field.focusListeners.size)
  }

  @Test
  @Timeout(30)
  fun mnemonicShortcutsGoToTheField(): Unit = withPopupScope { scope ->
    val field = SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR)
    val content = createPopupContent(field, scope)
    val action = DumbAwareAction.create("Ca_se sensitive") { }

    content.registerMnemonicShortcut(action)
    assertTrue(ActionUtil.getActions(field).contains(action))
    assertFalse(ActionUtil.getActions(content).contains(action))

    Disposer.dispose(content)
    assertFalse(ActionUtil.getActions(field).contains(action))
  }

  @Test
  @Timeout(30)
  fun fieldSuppliesThePopupData(@TestDisposable disposable: Disposable): Unit = withPopupScope { scope ->
    // The headless data manager ignores the component. The production one builds the context from the component tree.
    HeadlessDataManager.fallbackToProductionDataManager(disposable)
    val field = SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR)
    val content = createPopupContent(field, scope)
    field.text = "abc"

    val fieldContext = DataManager.getInstance().getDataContext(field)
    assertEquals("abc", fieldContext.getData(PlatformDataKeys.PREDEFINED_TEXT))
    assertNotNull(fieldContext.getData(SeDataKeys.SPLIT_SE_SELECTED_ITEMS))

    Disposer.dispose(content)
    assertNull(DataManager.getInstance().getDataContext(field).getData(PlatformDataKeys.PREDEFINED_TEXT))
  }

  @Test
  @Timeout(30)
  fun fieldCarriesTheResultsPopupForHintPlacement(): Unit = withPopupScope { scope ->
    val field = SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR)
    val content = createPopupContent(field, scope)
    val popup = JBPopupFactory.getInstance().createComponentPopupBuilder(content, null).createPopup()

    assertNull(PopupUtil.getPopupContainerFor(field))
    field.attachPopup(popup)
    assertEquals(popup, PopupUtil.getPopupContainerFor(field))

    field.endSession()
    assertNull(PopupUtil.getPopupContainerFor(field))

    Disposer.dispose(popup)
    Disposer.dispose(content)
  }

  @Test
  @Timeout(30)
  fun fieldExposesTheResultListToAssistiveTechnology(): Unit = withPopupScope { scope ->
    val field = SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR)
    val accessibleContext = requireNotNull(field.accessibleContext)
    assertInstanceOf(TextFieldWithListAccessibleContext::class.java, accessibleContext)
    assertNull(accessibleContext.accessibleSelection)

    val content = createPopupContent(field, scope)
    assertNotNull(accessibleContext.accessibleSelection)

    Disposer.dispose(content)
    assertNull(accessibleContext.accessibleSelection)
  }

  @Test
  @Timeout(30)
  fun registeredHintPopupBelongsToThePopup(): Unit = withPopupScope { scope ->
    val field = SeToolbarSearchField(ActionPlaces.MAIN_TOOLBAR)
    val content = createPopupContent(field, scope)
    val hintContent = JPanel()
    val hint = JBPopupFactory.getInstance().createComponentPopupBuilder(hintContent, null).createPopup()
    val stranger = JPanel()

    assertFalse(content.isPopupComponent(hintContent))
    field.registerHint(hint)
    assertTrue(content.isPopupComponent(hintContent))
    assertFalse(content.isPopupComponent(stranger))

    Disposer.dispose(hint)
    Disposer.dispose(content)
  }

  private fun withPopupScope(block: suspend (CoroutineScope) -> Unit): Unit = timeoutRunBlocking {
    val scope = childScope("SeToolbarSearchFieldTest")
    try {
      withContext(Dispatchers.UI) {
        block(scope)
      }
    }
    finally {
      scope.cancel()
    }
  }

  private fun createPopupContent(field: SeToolbarSearchField, scope: CoroutineScope): SePopupContentPane {
    val allTab = SeDummyTabVm(SeAllTab.ID, SeTabInfo(SeAllTab.PRIORITY, SeAllTab.NAME))
    return SePopupContentPane(
      project = null,
      resizePopupHandler = { },
      searchStatePublisher = SeSearchStatePublisher(),
      coroutineScope = scope,
      initialTabs = listOf(allTab),
      selectedTabId = SeAllTab.ID,
      initialSearchText = null,
      selectSearchText = true,
      initPopupExtendedSize = null,
      initialSelectionState = null,
      externalTextField = field,
    )
  }
}
