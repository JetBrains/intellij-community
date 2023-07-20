// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LeakingThis")

package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.BackgroundableDataProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.util.FileContentUtilCore
import com.intellij.util.ui.JBSwingUtilities
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.*
import javax.swing.JComponent
import javax.swing.JLayeredPane

private val LOG = logger<TextEditorComponent>()

@Internal
open class TextEditorComponent(
  private val project: Project,
  val file: VirtualFile,
  private val textEditor: TextEditorImpl,
  private val editorImpl: EditorImpl,
) : JLayeredPane(), DataProvider, Disposable, BackgroundableDataProvider {
  /**
   * Whether the editor's document is modified or not
   */
  var isModified: Boolean = false
    private set

  /**
   * Whether the editor is valid or not
   */
  private var isValid: Boolean
  private val editorHighlighterUpdater: EditorHighlighterUpdater

  @Volatile
  var isDisposed: Boolean = false
    private set

  init {
    layout = GridBagLayout()
    // to be able to set background for JLayeredPane
    isOpaque = true
    val editor = editorImpl
    editor.component.isFocusable = false
    editor.document.addDocumentListener(MyDocumentListener(), this)
    (editor.markupModel as EditorMarkupModel).isErrorStripeVisible = true
    editor.gutterComponentEx.setForceShowRightFreePaintersArea(true)
    editor.setFile(file)
    editor.contextMenuGroupId = IdeActions.GROUP_EDITOR_POPUP
    editor.setDropHandler(FileDropHandler(editor))
    TextEditorProvider.putTextEditor(editor, textEditor)

    super.add(editor.component, GridBagConstraints().also {
      it.gridx = 0
      it.gridy = 0
      it.weightx = 1.0
      it.weighty = 1.0
      it.fill = GridBagConstraints.BOTH
    })

    isModified = isModifiedImpl
    isValid = isEditorValidImpl
    LOG.assertTrue(isValid)
    val virtualFileListener = MyVirtualFileListener()
    file.fileSystem.addVirtualFileListener(virtualFileListener)
    Disposer.register(this) { file.fileSystem.removeVirtualFileListener(virtualFileListener) }
    editorHighlighterUpdater = EditorHighlighterUpdater(project, this, editor, file)
    project.messageBus.connect(this).subscribe(FileTypeManager.TOPIC, MyFileTypeListener())
  }

  final override fun add(comp: Component): Component {
    throw IllegalCallerException()
  }

  final override fun add(comp: Component, constraints: Any) {
    throw IllegalCallerException()
  }

  @Suppress("FunctionName", "unused")
  @Experimental
  @Internal
  fun __add(component: Component, constraints: Any) {
    super.add(component, constraints)
  }

  internal fun addLoadingDecoratorUi(component: JComponent) {
    putLayer(component, DRAG_LAYER)
    super.add(component, GridBagConstraints().also {
      it.gridx = 0
      it.gridy = 0
      it.anchor = GridBagConstraints.CENTER
    })
  }

  /**
   * Disposes all resources allocated be the TextEditorComponent. It disposes all created
   * editors, unregisters listeners. The behaviour of the splitter after disposing is unpredictable.
   */
  override fun dispose() {
    disposeEditor()
    isDisposed = true
  }

  /**
   * @return most recently used editor
   */
  val editor: EditorEx
    get() = editorImpl

  private fun disposeEditor() {
    EditorFactory.getInstance().releaseEditor(editorImpl)
  }

  /**
   * Just calculates "modified" property
   */
  private val isModifiedImpl: Boolean
    get() = FileDocumentManager.getInstance().isFileModified(file)

  /**
   * Updates "modified" property and fires event if necessary
   */
  fun updateModifiedProperty() {
    val oldModified = isModified
    isModified = isModifiedImpl
    textEditor.firePropertyChange(FileEditor.PROP_MODIFIED, oldModified, isModified)
  }

  /**
   * Name `isValid` is in use in `java.awt.Component`
   * so we change the name of method to `isEditorValid`
   *
   * @return whether the editor is valid or not
   */
  val isEditorValid: Boolean
    get() = isValid && !editorImpl.isDisposed

  private val isEditorValidImpl: Boolean
    /**
     * Just calculates
     */
    get() = FileDocumentManager.getInstance().getDocument(file) != null

  private fun updateValidProperty() {
    val oldValid = isValid
    isValid = isEditorValidImpl
    textEditor.firePropertyChange(FileEditor.PROP_VALID, oldValid, isValid)
  }

  override fun createBackgroundDataProvider(): DataProvider? {
    if (editorImpl.isDisposed) {
      return null
    }

    // There's no FileEditorManager for default project (which is used in diff command-line application)
    val fileEditorManager = if (!project.isDisposed && !project.isDefault) FileEditorManager.getInstance(project) else null
    val currentCaret = editorImpl.caretModel.currentCaret
    return DataProvider { dataId ->
      if (fileEditorManager != null) {
        val o = fileEditorManager.getData(dataId, editorImpl, currentCaret)
        if (o != null) return@DataProvider o
      }
      if (CommonDataKeys.EDITOR.`is`(dataId)) {
        return@DataProvider editorImpl
      }
      if (CommonDataKeys.VIRTUAL_FILE.`is`(dataId)) {
        return@DataProvider if (file.isValid) file else null // fix for SCR 40329
      }
      null
    }
  }

  /**
   * Updates "modified" property
   */
  private inner class MyDocumentListener : DocumentListener {
    /**
     * We can reuse this runnable to decrease the number of allocated objects.
     */
    private val updateRunnable: Runnable
    private var isUpdateScheduled = false

    init {
      updateRunnable = Runnable {
        isUpdateScheduled = false
        updateModifiedProperty()
      }
    }

    override fun documentChanged(e: DocumentEvent) {
      if (!isUpdateScheduled) {
        // a document's timestamp is changed later on undo or PSI changes
        ApplicationManager.getApplication().invokeLater(updateRunnable)
        isUpdateScheduled = true
      }
    }
  }

  private inner class MyFileTypeListener : FileTypeListener {
    override fun fileTypesChanged(event: FileTypeEvent) {
      // File can be invalid after file type changing. The editor should be removed
      // by the FileEditorManager if it's invalid.
      updateValidProperty()
    }
  }

  /**
   * Updates "valid" property and highlighters (if necessary)
   */
  private inner class MyVirtualFileListener : VirtualFileListener {
    override fun propertyChanged(e: VirtualFilePropertyEvent) {
      if (VirtualFile.PROP_NAME == e.propertyName) {
        // File can be invalidated after file changes name (an extension also can change). The editor should be removed if it's invalid.
        updateValidProperty()
        if (e.file == file &&
            (FileContentUtilCore.FORCE_RELOAD_REQUESTOR == e.requestor || !Comparing.equal<Any>(e.oldValue, e.newValue))) {
          editorHighlighterUpdater.updateHighlighters()
        }
      }
    }

    override fun contentsChanged(event: VirtualFileEvent) {
      // commit
      if (event.isFromSave) {
        ApplicationManager.getApplication().assertIsDispatchThread()
        val file = event.file
        LOG.assertTrue(file.isValid)
        if (file == this@TextEditorComponent.file) {
          updateModifiedProperty()
        }
      }
    }
  }

  // Swing calls us _before_ mine constructor
  @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
  override fun getBackground(): Color? = editorImpl?.backgroundColor ?: super.getBackground()

  override fun getComponentGraphics(g: Graphics): Graphics = JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
}