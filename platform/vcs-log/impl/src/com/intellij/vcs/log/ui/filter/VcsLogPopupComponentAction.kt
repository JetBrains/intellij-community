// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.filter

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.NlsActions
import com.intellij.ui.ClientProperty
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.util.function.Supplier
import javax.swing.JComponent

/**
 * Action class which allows to place [VcsLogPopupComponent] on a toolbar and supports "Quick Actions Popup".
 */
@ApiStatus.Internal
abstract class VcsLogPopupComponentAction(dynamicText: Supplier<@Nls @NlsActions.ActionText String>)
  : DumbAwareAction(dynamicText), CustomComponentAction {

  override fun actionPerformed(e: AnActionEvent) {
    val targetComponent = getTargetComponent(e) ?: return

    val actionComponent = UIUtil.uiTraverser(targetComponent).traverse().find { component: Component ->
      ClientProperty.get(component, CustomComponentAction.ACTION_KEY) === this
    }
    if (actionComponent is VcsLogPopupComponent) {
      actionComponent.showPopupMenu()
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    component.isEnabled = presentation.isEnabled
  }

  protected abstract fun getTargetComponent(e: AnActionEvent): JComponent?
}