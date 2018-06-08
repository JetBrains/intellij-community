// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.ide.TextCopyProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformDataKeys.COPY_PROVIDER
import com.intellij.openapi.application.ApplicationManager.getApplication
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.actions.ContentChooser.RETURN_SYMBOL
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.openapi.util.text.StringUtil.first
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.speedSearch.SpeedSearchUtil.applySpeedSearchHighlighting
import com.intellij.util.ObjectUtils.sentinel
import com.intellij.util.containers.nullize
import com.intellij.util.ui.JBUI.scale
import com.intellij.vcs.commit.CommitMessageInspectionProfile.getSubjectRightMargin
import java.awt.Point
import javax.swing.JList
import javax.swing.ListSelectionModel.SINGLE_SELECTION

/**
 * Action showing the history of recently used commit messages. Source code of this class is provided
 * as a sample of using the [CheckinProjectPanel] API. Actions to be shown in the commit dialog
 * should be added to the `Vcs.MessageActionGroup` action group.
 */
class ShowMessageHistoryAction : DumbAwareAction() {
  init {
    isEnabledInModalContext = true
  }

  override fun update(e: AnActionEvent) {
    val project = e.project
    val commitMessage = getCommitMessage(e)

    e.presentation.isVisible = project != null && commitMessage != null
    e.presentation.isEnabled = e.presentation.isVisible && !VcsConfiguration.getInstance(project!!).recentMessages.isEmpty()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val commitMessage = getCommitMessage(e)!!

    createPopup(project, commitMessage, VcsConfiguration.getInstance(project).recentMessages.reversed())
      .showInBestPositionFor(e.dataContext)
  }

  private fun createPopup(project: Project, commitMessage: CommitMessage, messages: List<String>): JBPopup {
    var chosenMessage: String? = null
    var selectedMessage: String? = null
    val rightMargin = getSubjectRightMargin(project)
    val previewCommandGroup = sentinel("Preview Commit Message")

    return JBPopupFactory.getInstance().createPopupChooserBuilder(messages)
      .setFont(commitMessage.editorField.editor?.colorsScheme?.getFont(EditorFontType.PLAIN))
      .setSelectionMode(SINGLE_SELECTION)
      .setItemSelectedCallback {
        selectedMessage = it
        it?.let { preview(project, commitMessage, it, previewCommandGroup) }
      }
      .setItemChosenCallback { chosenMessage = it }
      .setRenderer(object : ColoredListCellRenderer<String>() {
        override fun customizeCellRenderer(list: JList<out String>, value: String, index: Int, selected: Boolean, hasFocus: Boolean) {
          append(first(convertLineSeparators(value, RETURN_SYMBOL), rightMargin, false))

          applySpeedSearchHighlighting(list, this, true, selected)
        }
      })
      .addListener(object : JBPopupListener {
        override fun beforeShown(event: LightweightWindowEvent) {
          val popup = event.asPopup()
          val relativePoint = RelativePoint(commitMessage.editorField, Point(0, -scale(3)))
          val screenPoint = Point(relativePoint.screenPoint).apply { translate(0, -popup.size.height) }

          popup.setLocation(screenPoint)
        }

        override fun onClosed(event: LightweightWindowEvent) {
          // Use invokeLater() as onClosed() is called before callback from setItemChosenCallback
          getApplication().invokeLater { chosenMessage ?: cancelPreview(project, commitMessage) }
        }
      })
      .setNamerForFiltering { it }
      .createPopup()
      .apply {
        setDataProvider { dataId ->
          when (dataId) {
          // default list action does not work as "CopyAction" is invoked first, but with other copy provider
            COPY_PROVIDER.name -> object : TextCopyProvider() {
              override fun getTextLinesToCopy() = listOfNotNull(selectedMessage).nullize()
            }
            else -> null
          }
        }
      }
  }

  private fun preview(project: Project,
                      commitMessage: CommitMessage,
                      message: String,
                      groupId: Any) =
    CommandProcessor.getInstance().executeCommand(project, {
      commitMessage.setCommitMessage(message)
      commitMessage.editorField.selectAll()
    }, "", groupId, commitMessage.editorField.document)

  private fun cancelPreview(project: Project, commitMessage: CommitMessage) {
    val manager = UndoManager.getInstance(project)
    val fileEditor = commitMessage.editorField.editor?.let { TextEditorProvider.getInstance().getTextEditor(it) }

    if (manager.isUndoAvailable(fileEditor)) manager.undo(fileEditor)
  }

  private fun getCommitMessage(e: AnActionEvent) = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
}
