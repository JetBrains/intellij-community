package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import org.intellij.lang.annotations.Language


fun Finder.comboBox(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JComboBox']",
                                                                  JComboBoxUiComponent::class.java)

class JComboBoxUiComponent(data: ComponentData) : UiComponent(data) {
  private val fixture by lazy { driver.new(JComboBoxFixtureRef::class, robotService.robot, component) }
  fun selectItem(text: String) = fixture.select(text)
  fun selectItemContains(text: String) {
    if (fixture.listValues().singleOrNull { it.contains(text) } == null) {
      click()
    }
    fixture.select(fixture.listValues().single { it.contains(text) })
  }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JComboBoxTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JComboBoxFixtureRef {
  fun selectedText(): String
  fun listValues(): List<String>
  fun select(text: String)
}