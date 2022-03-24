// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.ApiStatus
import java.awt.GridBagLayout
import javax.swing.JComponent
import kotlin.properties.Delegates

@Deprecated("Proper controller is better")
@ApiStatus.ScheduledForRemoval
open class DisposingWrapper(parentDisposable: Disposable) : BorderLayoutPanel() {

  private var contentDisposable by Delegates.observable<Disposable?>(null) { _, oldValue, newValue ->
    if (oldValue != null) Disposer.dispose(oldValue)
    if (newValue != null) Disposer.register(parentDisposable, newValue)
  }

  fun setCenteredContent(content: JComponent) {
    contentDisposable = null
    setContent(NonOpaquePanel(GridBagLayout()).apply { add(content) })
  }

  fun setContent(content: JComponent, disposable: Disposable) {
    contentDisposable = disposable
    setContent(content)
  }

  private fun setContent(content: JComponent) {
    removeAll()
    addToCenter(content)
    validate()
  }
}