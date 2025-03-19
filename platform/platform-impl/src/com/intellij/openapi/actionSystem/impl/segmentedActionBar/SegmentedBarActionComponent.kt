// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

@ApiStatus.Internal
open class SegmentedBarActionComponent : AnAction(), CustomComponentAction, DumbAware {
  enum class ControlBarProperty {
    FIRST,
    LAST,
    MIDDLE,
    SINGLE
  }

  private val group: ActionGroup = object : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      val actions = mutableListOf<AnAction>()
      actionGroup?.let {
        actions.add(it)
      }
      return actions.toTypedArray()
    }
  }

  protected var actionGroup: ActionGroup? = null


  override fun actionPerformed(e: AnActionEvent) {
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = actionGroup != null
    e.presentation.isEnabled = e.isFromActionToolbar
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return JPanel(BorderLayout()).apply {
      val bar = createSegmentedActionToolbar(presentation, place, group).apply {
        setForceMinimumSize(true)
      }
      bar.targetComponent = bar
      add(bar.component, BorderLayout.CENTER)
    }
  }

  protected open fun createSegmentedActionToolbar(presentation: Presentation,
                                                  place: String,
                                                  group: ActionGroup): SegmentedActionToolbarComponent {
    return SegmentedActionToolbarComponent(place, group)
  }
}