// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBInsets
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Insets
import java.awt.event.ActionEvent
import javax.accessibility.Accessible
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.UIManager
import kotlin.properties.Delegates

open class ToolbarComboButton(val model: ToolbarComboButtonModel) : AbstractToolbarCombo(), Accessible {
  var margin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)

  internal var preferredHeightSupplier: (() -> Int)? = null

  var accessibleNamePrefix: @Nls String? = null

  override fun getUIClassID(): String = "ToolbarComboButtonUI"

  init {
    updateUI()
    model.addChangeListener {
      invalidate()
      repaint()
    }
  }

  override fun getLeftGap(): Int = insets.left + margin.left

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleJComponent(), AccessibleAction {
        override fun getAccessibleName(): String? =
          when {
            accessibleName != null -> accessibleName
            accessibleNamePrefix != null && !text.isNullOrEmpty() -> UIBundle.message("toolbar.combo.button.accessible.name.with.prefix",
                                                                                      accessibleNamePrefix, text)
            !text.isNullOrEmpty() -> UIBundle.message("toolbar.combo.button.accessible.name", text)
            else -> null
          }

        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PUSH_BUTTON

        override fun getAccessibleAction(): AccessibleAction = this

        override fun getAccessibleActionCount(): Int = 1

        override fun getAccessibleActionDescription(i: Int): String? =
          when {
            i == 0 -> UIManager.getString("AbstractButton.clickText")
            else -> null
          }

        override fun doAccessibleAction(i: Int): Boolean {
          if (i == 0) {
            val ae = ActionEvent(this@ToolbarComboButton, 0, null, System.currentTimeMillis(), 0)
            model.getActionListeners().forEach { l -> l.actionPerformed(ae) }
            return true
          }
          return false
        }
      }
    }
    return accessibleContext
  }
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
