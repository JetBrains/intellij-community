package com.intellij.driver.sdk.ui.components.common.editor

import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.JListUiComponent
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.completionList(@Language("xpath") xpath: String? = null, action: JListUiComponent.() -> Unit = {}): JListUiComponent {
  return list(xpath ?: "//div[@class='HeavyWeightWindow']//div[@class='LookupList']").apply(action)
}

/** Waits until each of [fragments] is a part of at least one item of the list. On a timeout, the message lists the items. */
fun JListUiComponent.waitItemsContain(fragments: List<String>, timeout: Duration = 30.seconds) {
  waitFor("The list has items with $fragments", timeout = timeout,
          errorMessage = { "The list items: ${runCatching { items }.getOrDefault(emptyList())}" }) {
    val items = runCatching { items }.getOrDefault(emptyList())
    fragments.all { fragment -> items.any { it.contains(fragment) } }
  }
}
