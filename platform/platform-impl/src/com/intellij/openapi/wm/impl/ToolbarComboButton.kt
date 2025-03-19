// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import java.awt.Insets
import kotlin.properties.Delegates

open class ToolbarComboButton(val model: ToolbarComboButtonModel) : AbstractToolbarCombo() {
  var margin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)

  internal var preferredHeightSupplier: (() -> Int)? = null

  override fun getUIClassID(): String = "ToolbarComboButtonUI"

  init {
    updateUI()
    model.addChangeListener {
      invalidate()
      repaint()
    }
  }

  override fun getLeftGap(): Int = insets.left + margin.left
}

@ApiStatus.Internal
abstract class ListenableToolbarComboButton(model: ToolbarComboButtonModel) : ToolbarComboButton(model) {

  private var disposable: Disposable? = null

  override fun addNotify() {
    super.addNotify()

    if (disposable != null) {
      logger<ListenableToolbarComboButton>().warn("ListenableToolbarWidget.addNotify: already connected, " +
                                                  "looks like the component was added without removing it")
      return
    }

    val project = ProjectUtil.getProjectForComponent(this)
    val newDisposable = Disposer.newDisposable()
    installListeners(project, newDisposable)

    disposable = newDisposable
  }

  override fun removeNotify() {
    super.removeNotify()

    disposable?.let { Disposer.dispose(it) }
    disposable = null
  }

  protected abstract fun installListeners(project: Project?, disposable: Disposable)

  fun updateWidgetAction() {
    ActionToolbar.findToolbarBy(this)?.updateActionsImmediately()
  }
}
