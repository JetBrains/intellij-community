package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.JTableUiComponent
import com.intellij.driver.sdk.ui.xQuery
import org.intellij.lang.annotations.Language

fun Finder.vcsLogGraphTable(@Language("xpath") xpath: String? = null, action: VcsLogGraphTable.() -> Unit) {
  x(xpath ?: xQuery { byClass("VcsLogMainGraphTable") }, VcsLogGraphTable::class.java).action()
}

fun Finder.vcsLogGraphTable(@Language("xpath") xpath: String? = null): VcsLogGraphTable {
  return x(xpath ?: xQuery { byClass("VcsLogMainGraphTable") }, VcsLogGraphTable::class.java)
}

class VcsLogGraphTable(data: ComponentData) : JTableUiComponent(data)
