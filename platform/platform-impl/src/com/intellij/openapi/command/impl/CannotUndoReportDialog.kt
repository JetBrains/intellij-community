// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.EditSourceOnEnterKeyHandler
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JList

@ApiStatus.Internal
class CannotUndoReportDialog(
  private val project: Project?,
  private val problemText: @Nls String,
  files: Collection<DocumentReference>,
) : DialogWrapper(project, false), UiDataProvider {

  private val problemFilesList = JBList<DocumentReference>()

  init {
    val model = DefaultListModel<DocumentReference>()
    for (file in files) {
      model.addElement(file)
    }
    problemFilesList.cellRenderer = object : SimpleListCellRenderer<DocumentReference>() {
      override fun customize(list: JList<out DocumentReference>,
                             file: DocumentReference,
                             index: Int,
                             selected: Boolean,
                             hasFocus: Boolean) {
        val vFile = file.file
        if (vFile != null) {
          text = vFile.presentableUrl
        }
        else {
          var content: CharSequence? = file.document?.immutableCharSequence
          if (content != null && content.length > FILE_TEXT_PREVIEW_CHARS_LIMIT) {
            content = "${content.subSequence(0, FILE_TEXT_PREVIEW_CHARS_LIMIT)}..."
          }
          text = IdeBundle.message("list.item.temporary.file.0", if (content == null) "" else " [$content]")
        }
      }
    }

    problemFilesList.model = model
    EditSourceOnDoubleClickHandler.install(problemFilesList) { doOKAction() }
    EditSourceOnEnterKeyHandler.install(problemFilesList) { doOKAction() }
    title = IdeBundle.message("cannot.undo.title")

    init()
  }

  override fun createActions(): Array<Action> = arrayOf(okAction)

  override fun createCenterPanel(): JComponent = panel {
    row {
      label(problemText)
        .applyToComponent { icon = Messages.getErrorIcon() }
    }
    group(IdeBundle.message("border.title.problem.files"), indent = false) {
      row {
        scrollCell(problemFilesList)
          .align(Align.FILL)
      }.resizableRow()
    }.resizableRow()
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink.lazy(CommonDataKeys.NAVIGATABLE) {
      val file = problemFilesList.selectedValue?.file
      if (project != null && file != null) OpenFileDescriptor(project, file) else null
    }
  }

  companion object {
    private const val FILE_TEXT_PREVIEW_CHARS_LIMIT = 40
  }
}
