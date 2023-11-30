package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import org.intellij.lang.annotations.Language

fun Finder.list(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JList']",
                                                              JListUiComponent::class.java)

class JListUiComponent(data: ComponentData) : UiComponent(data) {
  private val fixture by lazy { driver.new(JListFixtureRef::class, robotService.robot, component) }

  val items: List<String>
    get() = fixture.collectItems()

  val selectedItems: List<String>
    get() = fixture.collectSelectedItems()

  fun clickItem(itemText: String, fullMatch: Boolean = true) {
    findItemIndex(itemText, fullMatch)?.let {
      fixture.clickItemAtIndex(it)
    } ?: throw IllegalArgumentException("item with text $itemText not found")
  }

  private fun findItemIndex(itemText: String, fullMatch: Boolean): Int? =
    fixture.collectItems().indexOfFirst {
      if (fullMatch) it == itemText
      else it.contains(itemText, true)
    }.let {
      if (it == -1) null
      else it
    }
}

@Remote("com.jetbrains.performancePlugin.remotedriver.fixtures.JListTextFixture", plugin = REMOTE_ROBOT_MODULE_ID)
interface JListFixtureRef {
  fun collectItems(): List<String>
  fun collectSelectedItems(): List<String>
  fun clickItemAtIndex(index: Int)
}