package com.intellij.cce.ui

import com.intellij.cce.actions.ActionArraySerializer
import com.intellij.cce.actions.ActionSerializer
import com.intellij.cce.actions.ActionsBuilder.SessionBuilder
import com.intellij.cce.actions.CallFeature
import com.intellij.cce.actions.FileActions
import com.intellij.cce.core.SimpleTokenProperties
import com.intellij.cce.core.SymbolLocation
import com.intellij.cce.core.TypeProperty
import com.intellij.cce.evaluable.chat.PROMPT_PROPERTY
import com.intellij.cce.util.FileTextUtil.computeChecksum
import com.intellij.cce.util.FilesHelper
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JTextArea

@Suppress("HardCodedStringLiteral")
class EvaluationDatasetToolWindowFactory : ToolWindowFactory {
  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    val myToolWindow = EvaluationDatasetToolWindow(project)
    val contentFactory = ContentFactory.getInstance()
    val content = contentFactory.createContent(myToolWindow.getContent(), toolWindow.id, false)
    toolWindow.contentManager.addContent(content)
  }

  override suspend fun isApplicableAsync(project: Project): Boolean = ApplicationManager.getApplication().isInternal()

  class EvaluationDatasetToolWindow(private val project: Project) {
    private val myToolWindowContent: JPanel = JPanel(BorderLayout())
    private val datasetArea = JTextArea()
    private val leftButtonPanel = JPanel(GridLayout(6, 1))
    private val rightButtonPanel = JPanel(GridLayout(6, 1))

    private lateinit var session: SessionBuilder
    private lateinit var fileActions: FileActions
    private val actions = mutableListOf<FileActions>()

    init {
      val buttonPanel = JPanel(BorderLayout())
      myToolWindowContent.add(buttonPanel, BorderLayout.WEST)
      buttonPanel.add(leftButtonPanel, BorderLayout.WEST)
      buttonPanel.add(rightButtonPanel, BorderLayout.EAST)
      myToolWindowContent.add(JBScrollPane(datasetArea), BorderLayout.CENTER)

      startPage()
    }

    fun getContent(): JPanel = myToolWindowContent

    private fun startPage() {
      clearButtons()
      leftButtonPanel.add(createButton("Create Actions for File") {
        fileActions = createFileActions()
        newFileActionsPage()
      })
      displayDataset()
    }

    private fun newFileActionsPage() {
      clearButtons()
      leftButtonPanel.add(createButton("Create Session") {
        loadFileActions()
        session = SessionBuilder()
        newSessionPage()
      })
      rightButtonPanel.add(createButton("Cancel") {
        startPage()
      })
      rightButtonPanel.add(createButton("Save") {
        loadFileActions()
        actions.add(fileActions)
        startPage()
      })
      displayFileActions()
    }

    private fun newSessionPage() {
      clearButtons()
      leftButtonPanel.add(createActionButton("Move caret") {
        session.moveCaret(getCurrentOffset())
      })
      leftButtonPanel.add(createActionButton("Print text") {
        session.printText(getCurrentSelectionText())
      })
      leftButtonPanel.add(createActionButton("Delete range") {
        val (start, end) = getCurrentSelectionOffsets()
        session.deleteRange(start, end)
      })
      leftButtonPanel.add(createActionButton("Select range") {
        val (start, end) = getCurrentSelectionOffsets()
        session.selectRange(start, end)
      })
      leftButtonPanel.add(createActionButton("Rename") {
        session.rename(getCurrentOffset(), "_PLACEHOLDER_")
      })
      leftButtonPanel.add(createActionButton("Call feature") {
        session.callFeature(
          getCurrentSelectionText(),
          getCurrentOffset(),
          SimpleTokenProperties.create(TypeProperty.METHOD, SymbolLocation.PROJECT) {
            put(PROMPT_PROPERTY, "_PROMPT_")
          },
        )
      })
      rightButtonPanel.add(createButton("Cancel") {
        newFileActionsPage()
      })
      rightButtonPanel.add(createButton("Save") {
        val resultActions = fileActions.actions + session.build()
        fileActions = FileActions(fileActions.path, fileActions.checksum, resultActions.count { it is CallFeature }, resultActions)
        newFileActionsPage()
      })
      displaySession()
    }

    private fun createActionButton(text: String, action: () -> Unit): JButton {
      return createButton(text) {
        val editor = getCurrentEditor()
        if (editor == null) {
          Messages.showErrorDialog(project, "No opened editor", "Error")
          return@createButton
        }
        loadSession()
        action()
        displaySession()
      }
    }

    private fun createButton(text: String, action: () -> Unit): JButton {
      val button = JButton(text)
      button.addActionListener {
        action()
      }
      return button
    }

    private fun clearButtons() {
      leftButtonPanel.removeAll()
      rightButtonPanel.removeAll()
    }

    private fun displayDataset() {
      datasetArea.text = ActionArraySerializer.serialize(actions.toTypedArray())
    }

    private fun displayFileActions() {
      datasetArea.text = ActionSerializer.serializeFileActions(fileActions)
    }

    private fun displaySession() {
      datasetArea.text = ActionSerializer.serialize(session.build())
    }

    private fun createFileActions(): FileActions = FileActions(
      path = getCurrentEditor()?.virtualFile?.let { FilesHelper.getRelativeToProjectPath(project, it.path) } ?: "",
      checksum = getCurrentEditor()?.document?.text?.let { computeChecksum(it) } ?: "",
      sessionsCount = 0,
      actions = emptyList()
    )

    private fun loadSession() {
      val actions = ActionSerializer.deserialize(datasetArea.text)
      if (actions.isEmpty()) return
      session = SessionBuilder(actions.first().sessionId, actions.toMutableList())
    }

    private fun loadFileActions() {
      fileActions = ActionSerializer.deserializeFileActions(datasetArea.text)
    }

    private fun getCurrentOffset(): Int {
      val editor = getCurrentEditor()
      check(editor != null)
      return editor.caretModel.currentCaret.offset
    }

    private fun getCurrentSelectionOffsets(): Pair<Int, Int> {
      val editor = getCurrentEditor()
      check(editor != null)
      return (editor.selectionModel.selectionStart) to (editor.selectionModel.selectionEnd)
    }

    private fun getCurrentSelectionText(): String = getCurrentEditor()?.selectionModel?.selectedText ?: ""

    private fun getCurrentEditor(): Editor? = (FileEditorManager.getInstance(project).selectedEditor as? TextEditor)?.editor
  }
}
