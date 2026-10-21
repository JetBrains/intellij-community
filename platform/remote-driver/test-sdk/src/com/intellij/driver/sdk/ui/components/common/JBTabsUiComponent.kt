package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.TabInfoRef
import com.intellij.driver.sdk.ui.components.elements.TabLabelUi

fun Finder.jbTabs(locator: QueryBuilder.() -> String = { byType("com.intellij.ui.tabs.impl.JBTabsImpl") }): JBTabsUiComponent =
  x(JBTabsUiComponent::class.java, locator)

class JBTabsUiComponent(data: ComponentData) : UiComponent(data) {
  private val tabsComponent get() = driver.cast(component, JBTabsRef::class)

  val selectedTabName: String? get() = tabsComponent.getSelectedInfo()?.text

  fun tab(name: String): TabLabelUi =
    x(TabLabelUi::class.java) { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName(name)) }
}

@Remote("com.intellij.ui.tabs.impl.JBTabsImpl")
interface JBTabsRef {
  fun getSelectedInfo(): TabInfoRef?
}
