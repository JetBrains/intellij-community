// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder

import com.intellij.ide.BrowserUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.HyperlinkEvent

/**
 * Component properties for UI DSL
 */
@ApiStatus.Experimental
enum class DslComponentProperty {
  /**
   * A mark that component is a label created by [Row.label]
   */
  ROW_LABEL
}

@ApiStatus.Experimental
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
