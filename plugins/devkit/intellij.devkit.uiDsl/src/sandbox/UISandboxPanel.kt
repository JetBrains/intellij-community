// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.sandbox

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

internal interface UISandboxPanel {

  val title: String

  val isScrollbarNeeded: Boolean
    get() = true

  val isInternalApi: Boolean
    get() = false

  /**
   * One sentence that tells what the component is. The dialog shows it above the panel.
   */
  val description: @Nls String
    get() = ""

  /**
   * Short lines that tell when to use the component, and when not to use it. The dialog shows them as a list.
   */
  val usageNotes: List<@Nls String>
    get() = emptyList()

  fun createContent(disposable: Disposable): JComponent
}
