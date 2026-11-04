package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.client.utility
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.SwingUtilities
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.Icon
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

fun Finder.popup(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='HeavyWeightWindow']", PopupUiComponent::class.java)

fun Finder.popupLux(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='LuxFrontendWindow']", PopupUiComponent::class.java)

fun Finder.popups(): UIComponentsList<UiComponent> = xx("//div[@class='HeavyWeightWindow']")

fun Finder.popupMenu(locator: QueryBuilder.() -> String = { byClass("MyMenu") }) =
  x(xQuery(locator), PopupMenuUiComponent::class.java)

class PopupMenuUiComponent(data: ComponentData) : UiComponent(data) {
  private val menuItems =
    xx("//div[@class='ActionMenuItem' or @class='ActionMenu']", PopupItemUiComponent::class.java)

  fun getMenuItems(): List<PopupItemUiComponent> = menuItems.list()
  fun getSelectedPath(): List<String> =
    driver.utility(MenuSelectionManager::class).defaultManager().getSelectedPath().mapNotNull {
      it.getComponent().getAccessibleContext()?.getAccessibleName()
    }

  fun findMenuItemByText(text: String) = menuItems.list().firstOrNull { it.getText() == text }
                                         ?: throw AssertionError("No item with text '$text' found in popup '${this.searchContext}'")

  fun select(vararg items: String) {
    items.forEach { item ->
      waitForOne(message = "Find item: $item", timeout = 5.seconds,
              getter = { menuItems.list() },
              checker = { it.getText() == item })
        .click()
    }
  }

  fun selectContains(vararg items: String) {
    items.forEach { item ->
      waitForOne(message = "Find item: $item", timeout = 5.seconds,
                 getter = { menuItems.list() },
                 checker = { it.getText().contains(item) })
        .click()
    }
  }

  fun itemsList() = getMenuItems().map { it.getText() }

  @Remote("javax.swing.MenuSelectionManager")
  private interface MenuSelectionManager {
    fun defaultManager(): MenuSelectionManager
    fun getSelectedPath(): Array<MenuItem>
  }

  @Remote("javax.swing.MenuElement")
  private interface MenuItem {
    fun getComponent(): Component
  }
}

open class PopupUiComponent(data: ComponentData) : WindowUiComponent(data) {
  /**
   * Closes the popup and waits until its window no longer shows.
   *
   * The wait checks the window that this component found, not a new search by its xpath.
   * In the Split mode, a backend popup shows on the frontend as a copy.
   * For such a copy, [close] also waits until the backend closes the popup with the same data id.
   */
  fun close() {
    // One session keeps a hard reference to the window until the wait ends.
    driver.withContext {
      val window = component
      val backendPopupId = withContext(OnDispatcher.EDT) {
        val popup = utility<SwingUtilities>().getRootPane(window)?.let { utility<PopupUtil>().getPopupContainerFor(it) }
        if (popup == null) {
          dispose()
          return@withContext null
        }
        val backendPopupId = if (isRemDevMode) utility<RdPopupUtils>().getRdPopupId(popup)?.takeIf { hasBackendPopup(it) } else null
        popup.cancel()
        backendPopupId
      }
      waitFor(message = "The window of $this is closed", timeout = DEFAULT_FIND_TIMEOUT,
              getter = { window.isShowing() }, checker = { isShowing -> !isShowing })
      if (backendPopupId != null) {
        waitFor(message = "The backend popup of $this is closed", timeout = DEFAULT_FIND_TIMEOUT) {
          !withContext(OnDispatcher.EDT) { hasBackendPopup(backendPopupId) }
        }
      }
    }
  }

  private fun Driver.hasBackendPopup(dataId: Int): Boolean = service<RemoteDriverHostService>().hasBeControl(dataId)

  @Remote("com.intellij.openapi.ui.popup.util.PopupUtil")
  private interface PopupUtil {
    fun getPopupContainerFor(component: Component): JBPopup?
  }

  @Remote("com.intellij.openapi.ui.popup.JBPopup")
  private interface JBPopup {
    fun cancel()
  }

  @Remote("com.jetbrains.rd.ui.bindable.popup.PopupUtils", plugin = "com.intellij/intellij.rd.ui")
  private interface RdPopupUtils {
    fun getRdPopupId(popup: JBPopup): Int?
  }

  @Remote("com.intellij.cwm.plugin.driver.RemoteDriverHostService",
          plugin = "com.jetbrains.performancePlugin/intellij.driver.backend.split",
          rdTarget = RdTarget.BACKEND)
  private interface RemoteDriverHostService {
    fun hasBeControl(dataId: Int): Boolean
  }
}

@Remote("com.intellij.openapi.actionSystem.impl.ActionMenuItem")
interface PopupItemRef {
  fun isSelected(): Boolean
  fun getText(): String
  fun getIcon(): Icon
}

class PopupItemUiComponent(data: ComponentData) : UiComponent(data) {
  private val popupComponent by lazy { driver.cast(component, PopupItemRef::class) }

  fun getText() = popupComponent.getText()
  fun isSelected() = popupComponent.isSelected()
  fun getIconPath() = "path=(.*),".toRegex().find(popupComponent.getIcon().toString())?.let { it.groups.last()?.value ?: "empty" }
                      ?: "empty"

  override fun toString(): String = super.toString() + " '" + getText() + "'"
}