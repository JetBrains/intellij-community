// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.CommonBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DEFAULT_ACTION
import com.intellij.openapi.ui.DialogWrapper.createJButtonForAction
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.initOnShow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JRootPane
import javax.swing.tree.DefaultTreeModel

internal class OneShotMergeFlowDelegate(
  private val project: Project?,
  private val table: TreeTable,
  private val mergeDialogCustomizer: MergeDialogCustomizer,
  private val rootPane: JRootPane,
  private val files: List<VirtualFile>,
  private val onClose: () -> Unit,
  private val acceptForResolution: (MergeSession.Resolution) -> Unit,
  private val showMergeDialog: () -> Unit,
  private val toggleGroupByDirectory: (Boolean) -> Unit,
  private val getGroupByDirectory: () -> Boolean,
) : MergeFlowDelegate {

  private lateinit var acceptYoursButton: JButton
  private lateinit var acceptTheirsButton: JButton
  private lateinit var mergeButton: JButton

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(VcsBundle.message("merge.loading.merge.details")).applyToComponent {
          initOnShow("MultipleFileMergeDialog - Load Label") {
            @Suppress("HardCodedStringLiteral") // withContext loses the nls annotation
            val title = withContext(Dispatchers.Default) {
              mergeDialogCustomizer.getMultipleFileMergeDescription(files)
            }
            text = title
          }
        }
      }

      row {
        scrollCell(table)
          .align(Align.FILL)
          .resizableColumn()

        panel {
          row {
            acceptYoursButton = button(VcsBundle.message("multiple.file.merge.accept.yours")) {
              acceptForResolution(MergeSession.Resolution.AcceptedYours)
            }.align(AlignX.FILL)
              .component
          }
          row {
            acceptTheirsButton = button(VcsBundle.message("multiple.file.merge.accept.theirs")) {
              acceptForResolution(MergeSession.Resolution.AcceptedTheirs)
            }.align(AlignX.FILL)
              .component
          }
          row {
            val mergeAction = object : AbstractAction(VcsBundle.message("multiple.file.merge.merge")) {
              override fun actionPerformed(e: ActionEvent) {
                showMergeDialog()
              }
            }
            mergeAction.putValue(DEFAULT_ACTION, true)
            mergeButton = createJButtonForAction(mergeAction, rootPane)
            cell(mergeButton)
              .align(AlignX.FILL)
          }
        }.align(AlignY.TOP)
      }.resizableRow()

      if (project != null) {
        row {
          checkBox(VcsBundle.message("multiple.file.merge.group.by.directory.checkbox"))
            .selected(getGroupByDirectory())
            .applyToComponent {
              addChangeListener { toggleGroupByDirectory(isSelected) }
            }
        }
      }
    }.apply {
      // Temporary workaround for IDEA-302779
      minimumSize = JBUI.size(200, 150)
    }
  }

  override fun createActions(): List<Action> {
    val cancelAction = object : AbstractAction(CommonBundle.getCloseButtonText()) {
      override fun actionPerformed(e: ActionEvent) {
        onClose()
      }
    }.apply {
      putValue(DEFAULT_ACTION, true)
    }
    return listOf(cancelAction)
  }

  override fun onTreeChanged(selectedFiles: List<VirtualFile>, unmergeableFileSelected: Boolean, unacceptableFileSelected: Boolean) {
    val haveSelection = selectedFiles.any()
    acceptYoursButton.isEnabled = haveSelection && !unacceptableFileSelected
    acceptTheirsButton.isEnabled = haveSelection && !unacceptableFileSelected
    mergeButton.isEnabled = haveSelection && !unmergeableFileSelected
  }

  override fun buildTreeModel(
    project: Project?,
    grouping: ChangesGroupingPolicyFactory,
    unresolvedFiles: List<VirtualFile>,
  ): DefaultTreeModel = TreeModelBuilder.buildFromVirtualFiles(project, grouping, unresolvedFiles)
}