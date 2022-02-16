// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ide.BrowserUtil
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.layout.*
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent
import kotlin.reflect.KMutableProperty0

/**
 * Component properties for UI DSL
 */
enum class DslComponentProperty {
  /**
   * A mark that component is a label of a row, see [Panel.row]
   *
   * Value: true
   */
  ROW_LABEL,

  /**
   * Custom visual paddings, which are used instead of [JComponent.getInsets]
   *
   * Value: [Gaps]
   */
  VISUAL_PADDINGS
}

/**
 * Default comment width
 */
const val DEFAULT_COMMENT_WIDTH = 70

/**
 * Text uses word wrap if there is no enough width
 */
const val MAX_LINE_LENGTH_WORD_WRAP = -1

/**
 * Text is not wrapped and uses only html markup like <br>
 */
const val MAX_LINE_LENGTH_NO_WRAP = Int.MAX_VALUE

fun interface HyperlinkEventAction {

  companion object {
    /**
     * Opens URL in a browser
     */
    val HTML_HYPERLINK_INSTANCE = HyperlinkEventAction { e -> BrowserUtil.browse(e.url) }
  }

  fun hyperlinkActivated(e: HyperlinkEvent)

  @JvmDefault
  fun hyperlinkEntered(e: HyperlinkEvent) {
  }

  @JvmDefault
  fun hyperlinkExited(e: HyperlinkEvent) {
  }
}

/**
 * Converts property to nullable binding. Use this method if there is no chance null is set into resulting [PropertyBinding],
 * otherwise NPE will be thrown. See also safe overloaded [toNullableBinding] method with default value.
 *
 * Useful for [Cell<ComboBox>.bindItem(prop: KMutableProperty0<T?>)] if the ComboBox is not empty and the property is non-nullable
 */
fun <T> KMutableProperty0<T>.toNullableBinding(): PropertyBinding<T?> {
  return PropertyBinding({ get() }, { set(it!!) })
}

fun <T> KMutableProperty0<T>.toNullableBinding(defaultValue: T): PropertyBinding<T?> {
  return PropertyBinding({ get() }, { set(it ?: defaultValue) })
}
