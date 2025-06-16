package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.remoteDev.BeControlBuilder
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.BeControlComponentBase
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.UiText.Companion.asString
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language

fun Finder.ideStatusBar(@Language("xpath") xpath: String = "//div[@class='IdeStatusBarImpl']", action: IdeStatusBarUI.() -> Unit = {}): IdeStatusBarUI {
  return x(xpath, IdeStatusBarUI::class.java).apply(action)
}

class IdeStatusBarUI(data: ComponentData) : UiComponent(data) {
  private val leftStatusBar = x("//div[@class='StatusBarPanel'][1]")
  val widgetStatusBarPanel = x("//div[@class='StatusBarPanel'][2]", InfoAndProgressPanel.WidgetStatusBarPanel::class.java)
  val breadcrumbs = leftStatusBar.x("//div[@class='NavBarContainer']", NavBarContainerUI::class.java)


  val infoAndProgressPanel = leftStatusBar.x("//div[@class='InfoAndProgressPanelImpl']", InfoAndProgressPanel::class.java)

  class NavBarContainerUI(data: ComponentData) : UiComponent(data) {
    private val items = xx("//div[@class='NavBarItemComponent']")

    private val path: List<String>
      get() = items.list().sortedBy { it.component.x }.map { it.getAllTexts().asString(" ") }

    val pathString: String
      get() = path.joinToString(" -> ") { it }
  }

  class InfoAndProgressPanel(data: ComponentData) : UiComponent(data) {
    val statusPanel = x("//div[@class='TextPanel']", WidgetStatusBarPanel.WidgetUI::class.java)

    class WidgetStatusBarPanel(data: ComponentData) : UiComponent(data) {
      val widgets = xx(xQuery { byType("com.intellij.openapi.wm.impl.status.TextPanel") }, WidgetUI::class.java)
      val memoryUsagePanel = x("//div[@class='MemoryUsagePanelImpl']")


      fun findWidget(finder: WidgetFinder): WidgetUI = widgets.list().firstOrNull { finder.predicate(it) }
                                                       ?: throw AssertionError("Can't find '${finder.name}' widget")

      fun isWidgetPresented(finder: WidgetFinder): Boolean = widgets.list().firstOrNull { finder.predicate(it) } != null
      fun isWidgetNotPresented(finder: WidgetFinder): Boolean = isWidgetPresented(finder).not()
      fun assertWidgetIsPresented(finder: WidgetFinder) = assert(isWidgetPresented(finder)) {
        "Widget '${finder.name}' is not visible'"
      }
      fun assertWidgetIsNotPresented(finder: WidgetFinder) = assert(isWidgetNotPresented(finder)) {
        "Widget '${finder.name}' is visible'"
      }

      object Widgets {
        private fun widgetByTooltipContains(text: String): WidgetFinder = WidgetFinder(text) { it.toolTipText.contains(text) }
        val goToLine = widgetByTooltipContains("Go to Line")
        val lineSeparator = widgetByTooltipContains("Line Separator")
        val fileEncoding = widgetByTooltipContains("File Encoding")
        val editorConfig = WidgetFinder("Editor Config") { it.text.contains("spaces") }
        val readOnly = widgetByTooltipContains("Make file read-only")
        val writeable = widgetByTooltipContains("Make file writable")
        val gitBranch = widgetByTooltipContains("Git Branch")
        val projectStatus = widgetByTooltipContains("Project is configured")
        val projectStatusWarning = widgetByTooltipContains("This file does not belong to any project target")
      }

      data class WidgetFinder(val name: String, val predicate: (WidgetUI) -> Boolean)

      class WidgetUI(data: ComponentData) : UiComponent(data) {
        private val textPanelComponent by lazy { driver.cast(component, TextPanel::class) }
        val text: String
          get() = getAllTexts().asString(" ")

        val toolTipText: String
          get() = driver.withContext(OnDispatcher.EDT) { textPanelComponent.getToolTipText() ?: "" }

        val isHighlighted: Boolean
          get() = textPanelComponent.isHoverEffect()
      }
    }
  }
}

@Remote("com.intellij.openapi.wm.impl.status.TextPanel")
@BeControlClass(TextPanelClassBuilder::class)
interface TextPanel {
  fun getToolTipText(): String?
  fun isHoverEffect(): Boolean
}

class TextPanelClassBuilder : BeControlBuilder {
  override fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component {

    return TextPanelBeControl(driver, frontendComponent, backendComponent)
  }
}

class TextPanelBeControl(
  driver: Driver,
  frontendComponent: Component,
  backendComponent: Component
) : BeControlComponentBase(driver, frontendComponent, backendComponent), TextPanel {
  private val frontendTextPanel: TextPanel by lazy {
    driver.cast(onFrontend { byType("com.intellij.openapi.wm.impl.status.TextPanel") }.component, TextPanel::class)
  }

  override fun getToolTipText(): String? {
    return frontendTextPanel.getToolTipText()
  }
  override fun isHoverEffect(): Boolean {
    return frontendTextPanel.isHoverEffect()
  }
}
