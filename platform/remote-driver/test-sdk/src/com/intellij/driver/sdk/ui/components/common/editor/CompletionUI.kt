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

/** Waits until each of [fragments] is a part of at least one item of the list, and returns the items. On a timeout, the message names the missing fragments. */
fun JListUiComponent.waitItemsContain(fragments: List<String>, timeout: Duration = 30.seconds): List<String> {
  fun missing(items: List<String>) = fragments.filter { fragment -> items.none { it.contains(fragment) } }
  return waitFor("The list has items with $fragments", timeout = timeout,
                 errorMessage = { items -> "Not found: ${missing(items)}. The list items: $items" },
                 getter = { runCatching { items }.getOrDefault(emptyList()) },
                 checker = { items -> missing(items).isEmpty() })
}
