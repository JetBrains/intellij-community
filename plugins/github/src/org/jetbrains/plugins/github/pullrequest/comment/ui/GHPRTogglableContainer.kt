// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.comment.ui

import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.plugins.github.ui.util.SingleValueModel
import org.jetbrains.plugins.github.util.GithubUIUtil
import javax.swing.JComponent

object GHPRTogglableContainer {

  fun create(mainComponentSupplier: (SingleValueModel<Boolean>) -> JComponent,
             togglableComponentSupplier: (SingleValueModel<Boolean>) -> JComponent): JComponent {

    val toggleModel = SingleValueModel(false)
    val container = BorderLayoutPanel().apply {
      isOpaque = false
      addToCenter(mainComponentSupplier(toggleModel))
    }
    toggleModel.addValueChangedListener {
      if (toggleModel.value) {
        updateTogglableContainer(container, togglableComponentSupplier(toggleModel))
      }
      else {
        updateTogglableContainer(container, mainComponentSupplier(toggleModel))
      }
    }
    return container
  }

  private fun updateTogglableContainer(container: BorderLayoutPanel, component: JComponent) {
    with(container) {
      removeAll()
      addToCenter(component)
      revalidate()
      repaint()
      GithubUIUtil.focusPanel(component)
    }
  }
}