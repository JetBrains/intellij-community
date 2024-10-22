package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language
import javax.swing.JComboBox


fun Finder.comboBox(@Language("xpath") xpath: String? = null) =
  x(xpath ?: xQuery { byType(JComboBox::class.java) }, JComboBoxUiComponent::class.java)

fun Finder.comboBox(locator: QueryBuilder.() -> String) = x(JComboBoxUiComponent::class.java) { locator() }


class JComboBoxUiComponent(data: ComponentData) : UiComponent(data) {
  private val fixture by lazy { driver.new(JComboBoxFixtureRef::class, robot, component) }
  fun selectItem(text: String) = fixture.select(text)
  fun selectItemContains(text: String) {
    if (fixture.listValues().singleOrNull { it.contains(text) } == null) {
      click()
    }
    fixture.select(fixture.listValues().single { it.contains(text) })
  }

  fun listValues() = fixture.listValues()

  fun getSelectedItem() = fixture.selectedText()
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JComboBoxTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JComboBoxFixtureRef {
  fun selectedText(): String
  fun listValues(): List<String>
  fun select(text: String)
}