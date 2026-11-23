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
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UIComponentsList
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.Icon
import com.intellij.driver.sdk.ui.components.elements.PopupMenuUiComponent.Companion.POPUP_MENU_CLASS
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent.Companion.LUX_POPUP_CLASS
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent.Companion.POPUP_CLASS
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.xQuery
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.waitForOne
import org.intellij.lang.annotations.Language
import javax.swing.JPopupMenu

@Deprecated("Search the popup from its owner window or from UiRobot. Use WindowUiComponent.popup or UiRobot.popup.")
fun Finder.popup(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='HeavyWeightWindow']", PopupUiComponent::class.java)

@Deprecated("Search the popup from its owner window or from UiRobot. Use WindowUiComponent.popupLux or UiRobot.popupLux.")
fun Finder.popupLux(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='LuxFrontendWindow']", PopupUiComponent::class.java)

@Deprecated("Search the popups from their owner window. Use WindowUiComponent.popups.")
fun Finder.popups(): UIComponentsList<UiComponent> = xx("//div[@class='HeavyWeightWindow']")

@Deprecated("Search the menu from its owner window, from UiRobot or from the parent menu. " +
            "Use WindowUiComponent.popupMenu, UiRobot.popupMenu or PopupMenuUiComponent.popupMenu.")
fun Finder.popupMenu(locator: QueryBuilder.() -> String = { byClass("MyMenu") }): PopupMenuUiComponent =
  onPopupMenu(locator)

fun WindowUiComponent.popup(locator: QueryBuilder.() -> String = { byType(POPUP_CLASS) }): PopupUiComponent = onPopup(locator)

fun UiRobot.popup(locator: QueryBuilder.() -> String = { byType(POPUP_CLASS) }): PopupUiComponent = onPopup(locator)

fun WindowUiComponent.popupLux(locator: QueryBuilder.() -> String = { byType(LUX_POPUP_CLASS) }): PopupUiComponent =
  onPopupLux(locator)

fun UiRobot.popupLux(locator: QueryBuilder.() -> String = { byType(LUX_POPUP_CLASS) }): PopupUiComponent = onPopupLux(locator)

fun WindowUiComponent.popups(): UIComponentsList<PopupUiComponent> = xx(PopupUiComponent::class.java) { byType(POPUP_CLASS) }

fun WindowUiComponent.popupMenu(locator: QueryBuilder.() -> String = { byType(POPUP_MENU_CLASS) }): PopupMenuUiComponent =
  onPopupMenu(locator)

fun UiRobot.popupMenu(locator: QueryBuilder.() -> String = { byType(POPUP_MENU_CLASS) }): PopupMenuUiComponent = onPopupMenu(locator)

fun PopupMenuUiComponent.popupMenu(locator: QueryBuilder.() -> String = { byType(JPopupMenu::class.java) }): PopupMenuUiComponent =
  onPopupMenu(locator)

private fun Finder.onPopup(locator: QueryBuilder.() -> String): PopupUiComponent = x(PopupUiComponent::class.java, locator)

private fun Finder.onPopupLux(locator: QueryBuilder.() -> String): PopupUiComponent = x(PopupUiComponent::class.java, locator)

private fun Finder.onPopupMenu(locator: QueryBuilder.() -> String): PopupMenuUiComponent =
  x(xQuery(locator), PopupMenuUiComponent::class.java)

class PopupMenuUiComponent(data: ComponentData) : UiComponent(data) {
  private val menuItems: UIComponentsList<PopupItemUiComponent> =
    xx("//div[@class='ActionMenuItem' or @class='ActionMenu']", PopupItemUiComponent::class.java)

  fun getMenuItems(): List<PopupItemUiComponent> = menuItems.list()

  fun getSelectedPath(): List<String> =
    driver.utility(MenuSelectionManager::class).defaultManager().getSelectedPath().mapNotNull {
      it.getComponent().getAccessibleContext()?.getAccessibleName()
    }

  fun findMenuItemByText(text: String): PopupItemUiComponent =
    getMenuItems().firstOrNull { it.getText() == text }
    ?: throw AssertionError("No item with text '$text' found in popup '${this.searchContext}'")

  fun select(vararg items: String) {
    doSelect(*items) { actualMenuItem, item -> actualMenuItem == item }
  }

  fun selectContains(vararg items: String) {
    doSelect(*items) { actualMenuItem, item -> actualMenuItem.contains(item) }
  }

  fun itemsList() = getMenuItems().map { it.getText() }

  private fun doSelect(vararg items: String, predicate: (String, String) -> Boolean) {
    var currentMenu = this
    for (item in items) {
      waitForOne("Find menu item '$item'", getter = { currentMenu.getMenuItems() }, checker = { predicate(it.getText(), item) }).click()
      currentMenu = currentMenu.popupMenu()
    }
  }

  @Remote("javax.swing.MenuSelectionManager")
  private interface MenuSelectionManager {
    fun defaultManager(): MenuSelectionManager
    fun getSelectedPath(): Array<MenuItem>
  }

  @Remote("javax.swing.MenuElement")
  private interface MenuItem {
    fun getComponent(): Component
  }

  companion object {
    internal const val POPUP_MENU_CLASS = "com.intellij.openapi.actionSystem.impl.ActionPopupMenuImpl\$MyMenu"
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

  companion object {
    const val POPUP_CLASS = "javax.swing.Popup\$HeavyWeightWindow"
    const val LUX_POPUP_CLASS = "com.jetbrains.thinclient.lux.swing.LuxFrontendWindow"
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