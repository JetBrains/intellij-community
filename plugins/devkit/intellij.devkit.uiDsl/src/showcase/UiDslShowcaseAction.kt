// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.uiDsl.showcase

import com.intellij.devkit.uiDsl.DevkitUiDslBundle
import com.intellij.internal.Module
import com.intellij.internal.showSources
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBEmptyBorder
import java.awt.Dimension
import javax.swing.JComponent
import kotlin.reflect.KFunction
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.javaMethod

private val DEMOS = arrayOf(
  ::demoExamples,
  ::demoBasics,
  ::demoRowLayout,
  ::demoComponentLabels,
  ::demoComments,
  ::demoComponents,
  ::demoGaps,
  ::demoGroups,
  ::demoAvailability,
  ::demoValidation,
  ::demoBinding,
)

internal class UiDslShowcaseAction : DumbAwareAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    UiDslShowcaseDialog(e.project, templatePresentation.text).show()
  }
}

private class UiDslShowcaseDialog(val project: Project?, dialogTitle: @NlsContexts.DialogTitle String) :
  DialogWrapper(project, null, true, IdeModalityType.MODELESS, false) {

  init {
    title = dialogTitle
    init()
  }

  override fun createCenterPanel(): JComponent {
    val result = JBTabbedPane()
    result.minimumSize = Dimension(400, 300)
    result.preferredSize = Dimension(800, 600)

    for (demo in DEMOS) {
      addDemo(demo, result)
    }

    return result
  }

  private fun addDemo(demo: KFunction<DialogPanel>, tabbedPane: JBTabbedPane) {
    val annotation = demo.findAnnotation<Demo>()
    if (annotation == null) {
      throw Exception("Demo annotation is missed for ${demo.name}")
    }

    val content = panel {
      row {
        label(DevkitUiDslBundle.message("dialog.description", DevkitUiDslBundle.message(annotation.description)))
      }

      row {
        link(DevkitUiDslBundle.message("dialog.view.source")) {
          showSources(project, Module.DEVKIT_UI_DSL, demo.javaMethod!!.declaringClass)
        }
      }.bottomGap(BottomGap.MEDIUM)

      val args = demo.parameters.associateBy(
        { it },
        {
          when (it.name) {
            "parentDisposable" -> disposable
            else -> null
          }
        }
      )

      val dialogPanel = demo.callBy(args)
      dialogPanel.registerValidators(disposable)

      if (annotation.scrollbar) {
        row {
          dialogPanel.border = JBEmptyBorder(10)
          scrollCell(dialogPanel)
            .align(Align.FILL)
            .resizableColumn()
        }.resizableRow()
      }
      else {
        row {
          cell(dialogPanel)
            .align(AlignX.FILL)
            .resizableColumn()
        }
      }
    }

    tabbedPane.add(DevkitUiDslBundle.message(annotation.title), content)
  }
}
