// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem.impl.segmentedActionBar

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Graphics2D
import java.awt.Paint
import javax.swing.JComponent
import javax.swing.JPanel

open class SegmentedBarActionComponent : AnAction(), CustomComponentAction, DumbAware {
  companion object {
    @Deprecated("Use {@link SegmentedActionToolbarComponent#Companion#isCustomBar(Component)}",
                ReplaceWith("SegmentedActionToolbarComponent.isCustomBar(component)"))
    fun isCustomBar(component: Component): Boolean {
      return SegmentedActionToolbarComponent.isCustomBar(component)
    }

    @Deprecated("Use {@link SegmentedActionToolbarComponent#Companion#paintButtonDecorations(Component)}",
                ReplaceWith("SegmentedActionToolbarComponent.paintButtonDecorations(g, c, paint)"))
    fun paintButtonDecorations(g: Graphics2D, c: JComponent, paint: Paint): Boolean {
      return SegmentedActionToolbarComponent.paintButtonDecorations(g, c, paint)
    }
  }

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

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = actionGroup != null
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