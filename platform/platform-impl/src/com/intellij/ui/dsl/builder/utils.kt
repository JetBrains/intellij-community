// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ide.BrowserUtil
import com.intellij.ui.dsl.gridLayout.Gaps
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

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
