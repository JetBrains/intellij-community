package org.jetbrains.idea.devkit.stacktrace.editor

import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.threadDumpParser.ThreadDumpParser.parse
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory.getInstance
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.TabbedPaneContentUI
import com.intellij.unscramble.UnscrambleUtils.addConsole
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.idea.devkit.stacktrace.DevKitStackTraceBundle
import org.jetbrains.idea.devkit.stacktrace.getFreezeRunDescriptor
import org.jetbrains.idea.devkit.stacktrace.util.StackTracePluginScope
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants.TOP
import kotlin.time.Duration.Companion.milliseconds

class StackTraceFileEditor(private val project: Project, private val file: VirtualFile) : FileEditor, UserDataHolderBase() {
  private val document = FileDocumentManager.getInstance().getDocument(file) ?: error("Document not found for file: $file")
  private val mainPanelWrapper = JPanel(BorderLayout())
  private val mainEditor = MutableStateFlow<Editor?>(null)
  private val coroutineScope = StackTracePluginScope.createChildScope(project)
  private var myContentManager: ContentManager? = null

  init {
    document.addDocumentListener(ReparseContentDocumentListener(), this)
    coroutineScope.launch(Dispatchers.EDT) {
      myContentManager = getInstance().createContentManager(TabbedPaneContentUI(TOP), false, project)
      updateStacktracePane()
    }
  }

  fun setMainEditor(editor: Editor) {
    check(mainEditor.value == null) { "Main editor already set" }
    mainEditor.value = editor
  }

  override fun getComponent(): JComponent = mainPanelWrapper

  override fun getPreferredFocusedComponent(): JComponent? = myContentManager?.component ?: mainPanelWrapper

  override fun getName(): String = DevKitStackTraceBundle.message("stack.trace")

  override fun setState(state: FileEditorState) {}

  override fun isModified(): Boolean = false

  override fun isValid(): Boolean = true

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {}

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {}

  override fun getFile(): VirtualFile = file

  override fun dispose() {
    myContentManager?.removeAllContents(true)
    myContentManager?.let {
      Disposer.dispose(it)
    }
    myContentManager = null
    coroutineScope.cancel()
  }

  @RequiresEdt
  private suspend fun updateStacktracePane() {
    if (!file.isValid) return
    val contentManager = myContentManager ?: return
    myContentManager?.removeAllContents(true)

    addThreadContent(contentManager) ?: return
    addFreezeAnalysisContent(contentManager)

    mainPanelWrapper.add(contentManager.component, BorderLayout.CENTER)
    if (mainPanelWrapper.isShowing) mainPanelWrapper.validate()
    mainPanelWrapper.repaint()
  }

  private suspend fun addThreadContent(contentManager: ContentManager): Unit? = withContext(Dispatchers.Default) {
    val threadStates = parse(document.text)
    withContext(Dispatchers.EDT) {
      addConsole(project, threadStates, document.text, false)?.let { descriptor ->
        contentManager.addContent(createNewContent(descriptor).apply {
          executionId = descriptor.executionId
          component = descriptor.component
          setPreferredFocusedComponent(descriptor.preferredFocusComputable)
          putUserData(RunContentDescriptor.DESCRIPTOR_KEY, descriptor)
          displayName = descriptor.displayName
          descriptor.setAttachedContent(this)
        })
        Disposer.register(contentManager, descriptor)
      }
    }
  }

  private suspend fun addFreezeAnalysisContent(contentManager: ContentManager) {
    getFreezeRunDescriptor(document.text, project)?.let { freezeDescriptor ->
      contentManager.addContent(createNewContent(freezeDescriptor).apply {
        executionId = freezeDescriptor.executionId
        component = freezeDescriptor.component
        setPreferredFocusedComponent(freezeDescriptor.preferredFocusComputable)
        putUserData(RunContentDescriptor.DESCRIPTOR_KEY, freezeDescriptor)
        displayName = freezeDescriptor.displayName
      })
      Disposer.register(contentManager, freezeDescriptor)
    }
  }

  private fun createNewContent(descriptor: RunContentDescriptor): Content {
    val content = getInstance().createContent(
      descriptor.component, descriptor.displayName, true
    ).apply {
      putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
      isPinned = AdvancedSettings.getBoolean("start.run.configurations.pinned")
      icon = descriptor.icon
    }
    return content
  }

  private inner class ReparseContentDocumentListener : DocumentListener {
    @OptIn(FlowPreview::class)
    private val documentChangedRequests = MutableSharedFlow<Unit>(
      replay = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST
    ).apply {
      coroutineScope.launch(Dispatchers.EDT) {
        debounce(500.milliseconds)
          .collectLatest {
            updateStacktracePane()
          }
      }
    }

    override fun documentChanged(event: DocumentEvent) {
      documentChangedRequests.tryEmit(Unit)
    }
  }
}