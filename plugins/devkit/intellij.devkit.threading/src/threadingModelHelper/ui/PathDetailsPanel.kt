// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.threading.threadingModelHelper.ui

import com.intellij.devkit.threading.DevkitThreadingBundle
import com.intellij.execution.impl.AnalyzeStacktraceConsoleUtil
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiElement
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.idea.devkit.threadingModelHelper.ExecutionPath
import java.awt.CardLayout
import java.awt.datatransfer.StringSelection
import javax.swing.JPanel

private const val EMPTY_CARD = "empty"
private const val DETAILS_CARD = "details"

/**
 * Shows the method chain and the lock requirement of the selected [ExecutionPath].
 * The method chain is a stack trace in a console, so each frame is a hyperlink to the source.
 */
internal class PathDetailsPanel(private val project: Project, parentDisposable: Disposable) : JPanel(CardLayout()) {

  private var path: ExecutionPath? = null

  private val chainConsole: ConsoleView = AnalyzeStacktraceConsoleUtil.createConsole(project).also {
    Disposer.register(parentDisposable, it)
  }

  private val requirementLabel = JBLabel()

  init {
    val emptyPanel = JBPanelWithEmptyText().withEmptyText(DevkitThreadingBundle.message("empty.text.no.selection"))
    val detailsPanel = panel {
      row {
        label(DevkitThreadingBundle.message("details.title")).bold()
      }
      row {
        cell(chainConsole.component).align(Align.FILL)
      }.resizableRow()
      row {
        cell(requirementLabel)
      }
      row {
        button(DevkitThreadingBundle.message("details.button.go.to.lock.check")) { navigateToLockCheck() }
          .applyToComponent { icon = AllIcons.Actions.EditSource }
        button(DevkitThreadingBundle.message("details.button.copy.path")) { copyPath() }
          .applyToComponent { icon = AllIcons.Actions.Copy }
      }
    }.apply {
      border = JBUI.Borders.empty(8, 12)
    }
    add(emptyPanel, EMPTY_CARD)
    add(detailsPanel, DETAILS_CARD)
    showPath(null)
  }

  fun showPath(path: ExecutionPath?) {
    this.path = path
    if (path != null) {
      val stackTrace = path.methodChain.asReversed().joinToString("\n") { stackFrameText(it) }
      AnalyzeStacktraceConsoleUtil.printStacktrace(chainConsole, stackTrace, ConsoleViewContentType.NORMAL_OUTPUT)
      requirementLabel.icon = requirementIcon(path.lockRequirement)
      requirementLabel.text = requirementDescription(path)
    }
    (layout as CardLayout).show(this, if (path == null) EMPTY_CARD else DETAILS_CARD)
  }

  fun navigateToLockCheck() {
    val path = path ?: return
    WriteIntentReadAction.run { navigateTo(path.lockRequirement.source) }
  }

  private fun navigateTo(element: PsiElement) {
    if (!element.isValid) return
    val file = element.containingFile?.virtualFile ?: return
    OpenFileDescriptor(project, file, element.textOffset).navigate(true)
  }

  private fun copyPath() {
    val path = path ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(pathClipboardText(path)))
  }
}
