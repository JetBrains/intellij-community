// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.merge.flow

import com.intellij.CommonBundle
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper.DEFAULT_ACTION
import com.intellij.openapi.ui.DialogWrapper.createJButtonForAction
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.openapi.vcs.merge.MergeResolveActionPresentation
import com.intellij.openapi.vcs.merge.MergeResolveActionProvider
import com.intellij.openapi.vcs.merge.MergeResolveActionSupport
import com.intellij.openapi.vcs.merge.MergeResolveWithAgentContext
import com.intellij.openapi.vcs.merge.MergeSession
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
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
  private val tableComponent: JComponent,
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
            val title = withContext(Dispatchers.Default) {
              mergeDialogCustomizer.getMultipleFileMergeDescription(files)
            }
            text = title
          }
        }
      }

      row {
        scrollCell(tableComponent)
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
          createResolveActionButtons().forEach { button ->
            row {
              cell(button).align(AlignX.FILL)
            }
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

  override fun createSouthPanel(): JComponent? = null

  private fun createResolveActionButtons(): List<JComponent> {
    val project = project ?: return emptyList()
    val mergeContext = MergeResolveWithAgentContext(
      project = project,
      files = files,
      closeDialogForAgentHandoffHandler = onClose,
      isLaunchContextValidHandler = { rootPane.isDisplayable },
    )
    return MergeResolveActionProvider.EP_NAME.extensionList
      .sortedBy(MergeResolveActionProvider::order)
      .mapNotNull { provider -> createResolveActionComponent(provider, mergeContext) }
  }

  private fun createResolveActionComponent(
    provider: MergeResolveActionProvider,
    mergeContext: MergeResolveWithAgentContext,
  ): JComponent? {
    val action = provider.action
    return if (action is CustomComponentAction) {
      createResolveActionCustomComponent(action, mergeContext)
    }
    else {
      createResolveActionButton(provider, mergeContext)
    }
  }

  private fun createResolveActionButton(
    provider: MergeResolveActionProvider,
    mergeContext: MergeResolveWithAgentContext,
  ): JButton? {
    val button = JButton()
    updateResolveActionButton(provider, mergeContext, button)
    if (!button.isVisible) return null
    button.addActionListener {
      MergeResolveActionSupport.performAction(provider, mergeContext, button, MERGE_DIALOG_ACTION_PLACE)
      updateResolveActionButton(provider, mergeContext, button)
    }
    return button
  }

  private fun createResolveActionCustomComponent(
    action: CustomComponentAction,
    mergeContext: MergeResolveWithAgentContext,
  ): JComponent? {
    val anAction = action as? com.intellij.openapi.actionSystem.AnAction ?: return null
    val presentation = MergeResolveActionSupport.getUpdatedPresentation(anAction, mergeContext, null, MERGE_DIALOG_ACTION_PLACE)
                       ?: return null
    val component = action.createCustomComponent(presentation, MERGE_DIALOG_ACTION_PLACE)
    action.updateCustomComponent(component, presentation)
    return wrapResolveActionComponent(component, mergeContext)
  }

  private fun updateResolveActionButton(
    provider: MergeResolveActionProvider,
    mergeContext: MergeResolveWithAgentContext,
    button: JButton,
  ) {
    syncResolveActionButton(button, MergeResolveActionSupport.createActionPresentation(provider, mergeContext, button, MERGE_DIALOG_ACTION_PLACE))
  }

  private fun syncResolveActionButton(button: JButton, presentation: MergeResolveActionPresentation?) {
    if (presentation == null) {
      button.isVisible = false
      return
    }

    button.text = presentation.text
    button.icon = presentation.icon
    button.setToolTipText(presentation.description?.let(HtmlChunk::text))
    button.isEnabled = presentation.isEnabled
    button.isVisible = true
  }

  private fun wrapResolveActionComponent(
    component: JComponent,
    mergeContext: MergeResolveWithAgentContext,
  ): JComponent {
    return UiDataProvider.wrapComponent(component) { sink ->
      sink[CommonDataKeys.PROJECT] = mergeContext.project
      sink[PlatformCoreDataKeys.CONTEXT_COMPONENT] = component
      sink[MergeResolveWithAgentContext.KEY] = mergeContext
    }
  }

  private companion object {
    const val MERGE_DIALOG_ACTION_PLACE: String = "Merge.OneShotDialog"
  }
}