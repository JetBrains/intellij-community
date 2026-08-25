package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.DEFAULT_FIND_TIMEOUT
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.should
import kotlin.time.Duration

/**
 * [com.intellij.ui.components.breadcrumbs.Breadcrumbs] does not create a child component per crumb:
 * crumbs are kept in a private list and painted straight onto the Graphics.
 * That is why crumbs have neither text nor children in the Swing hierarchy, and the only way to read them
 * is the painted text.
 */
class BreadcrumbsUiComponent(data: ComponentData) : UiComponent(data) {

  /**
   * Crumb texts from left to right, e.g. `["Appearance & Behavior", "System Settings", "Data Sharing"]`.
   *
   * Sorting by `x` alone is intentional: the layout of `Breadcrumbs` puts every crumb into a single row,
   * while the baseline `y` is derived from the font of each crumb and may differ within that row.
   */
  val crumbs: List<String>
    get() = getAllTexts().sortedBy { it.point.x }.map { it.text }

  fun shouldHaveCrumbs(vararg expected: String, timeout: Duration = DEFAULT_FIND_TIMEOUT): BreadcrumbsUiComponent =
    shouldHaveCrumbs(expected.toList(), timeout)

  fun shouldHaveCrumbs(expected: List<String>, timeout: Duration = DEFAULT_FIND_TIMEOUT): BreadcrumbsUiComponent {
    var actual: List<String>? = null
    return should(message = "Breadcrumbs should be ${expected.joinToString(" | ")}",
                  timeout = timeout,
                  errorMessage = { "expected: $expected, but found: $actual" }) {
      actual = crumbs
      actual == expected
    }
  }
}
