// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl

import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBInsets
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.accessibility.Accessible
import javax.accessibility.AccessibleAction
import javax.accessibility.AccessibleContext
import javax.accessibility.AccessibleRole
import javax.swing.UIManager
import kotlin.properties.Delegates
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class ToolbarSplitButton(val model: ToolbarSplitButtonModel) : AbstractToolbarCombo(), Accessible {

  var separatorMargin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)
  var leftPartMargin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)
  var rightPartMargin: Insets by Delegates.observable(JBInsets.emptyInsets(), this::fireUpdateEvents)

  override fun getUIClassID(): String = "ToolbarSplitButtonUI"

  init {
    updateUI()
    model.addChangeListener {
      invalidate()
      repaint()
    }
  }

  override fun getLeftGap(): Int = insets.left + leftPartMargin.left

  internal fun doAction(modifiersEx: Int = 0): Boolean = fireEvent(model.getActionListeners(), modifiersEx)

  internal fun doExpand(modifiersEx: Int = 0): Boolean = fireEvent(model.getExpandListeners(), modifiersEx)

  private fun fireEvent(listeners: List<ActionListener>, modifiersEx: Int): Boolean {
    if (!isEnabled) {
      return false
    }
    val ae = ActionEvent(this, ActionEvent.ACTION_PERFORMED, null, System.currentTimeMillis(), modifiersEx)
    listeners.forEach { it.actionPerformed(ae) }
    return true
  }

  override fun getAccessibleContext(): AccessibleContext? {
    if (accessibleContext == null) {
      accessibleContext = object : AccessibleJComponent(), AccessibleAction {
        override fun getAccessibleName(): String? {
          return when {
            accessibleName != null -> accessibleName
            !text.isNullOrEmpty() -> UIBundle.message("toolbar.combo.button.accessible.name", text)
            !toolTipText.isNullOrEmpty() -> UIBundle.message("toolbar.combo.button.accessible.name", toolTipText)
            else -> null
          }
        }

        override fun getAccessibleDescription(): String {
          val expandDescription = UIBundle.message("toolbar.split.button.accessible.description.expand")
          val tooltip = toolTipText
          return if (accessibleName == null && !text.isNullOrEmpty() && !tooltip.isNullOrEmpty()) {
            UIBundle.message("toolbar.split.button.accessible.description.with.prefix", tooltip, expandDescription)
          }
          else {
            expandDescription
          }
        }

        override fun getAccessibleRole(): AccessibleRole = AccessibleRole.PUSH_BUTTON

        override fun getAccessibleAction(): AccessibleAction = this

        override fun getAccessibleActionCount(): Int = 2

        override fun doAccessibleAction(i: Int): Boolean =
          when (i) {
            0 -> doAction()
            1 -> doExpand()
            else -> false
          }

        override fun getAccessibleActionDescription(i: Int): String? =
          when (i) {
            0 -> UIManager.getString("AbstractButton.clickText")
            1 -> UIBundle.message("toolbar.split.button.accessible.expand.action")
            else -> null
          }
      }
    }
    return accessibleContext
  }
}