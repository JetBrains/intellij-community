package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTable
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.ide.IdeBundle
import javax.swing.JTextField

/** The columns of the table are Active, Name, Family and Path. */
private const val NAME_COLUMN: Int = 1

/**
 * Settings | Tools | Web Browsers and Preview page, see `com.intellij.ide.browsers.BrowserSettingsPanel`.
 * The page has no class of its own, so the lookup takes the settings content with the title of the 'Installed Browsers' group.
 * The table is not in the hierarchy while the group is collapsed, so the lookup cannot take the table.
 */
fun SettingsDialogUiComponent.webBrowsersSettingsPage(action: WebBrowsersSettingsPageUi.() -> Unit = {}): WebBrowsersSettingsPageUi =
  x(WebBrowsersSettingsPageUi::class.java, "Web Browsers settings page") {
    componentWithChild(byType("com.intellij.openapi.options.ex.ConfigurableCardPanel"),
                       contains(byAccessibleName(IdeBundle.message("settings.browsers.installed.browsers"))))
  }.apply(action)

class WebBrowsersSettingsPageUi(data: ComponentData) : UiComponent(data) {
  /**
   * The title of the collapsible 'Installed Browsers' group. On macOS the accessible name also tells the state of the group,
   * 'Show Installed Browsers' or 'Hide Installed Browsers', see `CollapsibleTitledSeparatorImpl`. So the lookup matches a part of it.
   */
  val installedBrowsersTitle: UiComponent = x("'Installed Browsers' group title") {
    contains(byAccessibleName(IdeBundle.message("settings.browsers.installed.browsers")))
  }

  /** The cells are read by the accessible name of the renderer, that is the full text of the cell. */
  val browsersTable: JTableUiComponent = accessibleTable { byType("com.intellij.ui.table.TableView") }

  /** The names of the browsers in the table. */
  val browserNames: List<String>
    get() = browsersTable.content().values.mapNotNull { it[NAME_COLUMN] }

  /** Expands the 'Installed Browsers' group when it is collapsed. The group can be collapsed by default. */
  fun expandInstalledBrowsers() {
    if (browsersTable.notPresent()) {
      installedBrowsersTitle.click()
    }
    browsersTable.waitFound()
  }

  /**
   * Renames the browser with the [name] to [newName] in the Name cell of the table.
   * The text is set directly, because typing depends on the keyboard layout of the machine.
   * The Family column can have the same text as the Name column, so the lookup takes the Name column only.
   * The change is applied by OK or Apply of Settings.
   */
  fun renameBrowser(name: String, newName: String) {
    val content = browsersTable.content()
    val row = content.entries.singleOrNull { it.value[NAME_COLUMN] == name }?.key
              ?: error("The table has no browser '$name': ${content.values}")
    browsersTable.doubleClickCell(row, NAME_COLUMN)
    browsersTable.textField("'Name' cell editor") { byType(JTextField::class.java) }.text = newName
    keyboard { enter() }
  }
}
