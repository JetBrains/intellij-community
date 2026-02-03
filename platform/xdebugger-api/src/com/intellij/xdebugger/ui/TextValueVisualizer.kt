// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

/**
 * Extension point `xdebugger.textValueVisualizer` is used to visualize or format plain text values * obtained
 * from debuggee's string-like entities.
 * E.g., JSON might be pretty-printed with syntax highlighting, HTML might be rendered in a browser and can be pretty-printed as XML.
 */
@ApiStatus.Experimental // until we consider collection visualizers
interface TextValueVisualizer {
  /**
   * Visualizes the given value, possibly in several ways.
   * Returns an empty list, if [value] cannot be visualized.
   */
  fun visualize(value: @NlsSafe String): List<VisualizedContentTab>

  /**
   * Try to detect the file type of given [value] visualizable by this visualizer.
   * It might be used to view/edit raw [value] without any visualization/formatting.
   */
  fun detectFileType(value: @NlsSafe String): FileType? = null
}

@ApiStatus.Experimental // until we consider collection visualizers
interface VisualizedContentTab {
  /** Title of the tab with the content. */
  val name: @Nls String

  /** Internal ID of the tab used to remember the last used visualizer. */
  val id: String

  /** Create the visualized content component. */
  fun createComponent(project: Project, parentDisposable: Disposable): JComponent

  /**
   * This callback is called when the tab is shown.
   * If user switches between tabs, `firstTime` is `true` only when the tab is shown for the first time.
   */
  fun onShown(project: Project, firstTime: Boolean) {
  }
}
