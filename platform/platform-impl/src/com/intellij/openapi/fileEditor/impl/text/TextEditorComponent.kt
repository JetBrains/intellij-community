// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("LeakingThis")

package com.intellij.openapi.fileEditor.impl.text

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.UiCompatibleDataProvider
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent
import com.intellij.util.FileContentUtilCore
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.ui.JBSwingUtilities
import kotlinx.coroutines.job
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLayeredPane

private val LOG = logger<TextEditorComponent>()

@Internal
open class TextEditorComponent(
  val file: VirtualFile,
  @JvmField internal val editorImpl: EditorImpl,
) : JLayeredPane(), UiCompatibleDataProvider {
  /**
   * Whether the editor's document is modified or not
   */
  var isModified: Boolean = false
    private set

  /**
   * Whether the editor is valid or not
   */
  private var isValid: Boolean

  @Volatile
  var isDisposed: Boolean = false
    private set

  init {
    layout = GridBagLayout()
    // to be able to set background for JLayeredPane
    isOpaque = true
    val editor = editorImpl
    editor.component.isFocusable = false
    (editor.markupModel as EditorMarkupModel).isErrorStripeVisible = true
    editor.gutterComponentEx.setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.SHOW)
    editor.setFile(file)
    editor.contextMenuGroupId = IdeActions.GROUP_EDITOR_POPUP
    editor.setDropHandler(FileEditorDropHandler(editor))

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
  }

  internal fun listenChanges(parentDisposable: Disposable, asyncLoader: AsyncEditorLoader, textEditor: TextEditorImpl, project: Project) {
    val messageBusConnection = project.messageBus.connect(asyncLoader.coroutineScope)

    val editorHighlighterUpdater = EditorHighlighterUpdater(
      project = project,
      parentDisposable = parentDisposable,
      connection = messageBusConnection,
      editor = editor,
      file = file,
      asyncLoader = asyncLoader,
    )

    val virtualFileListener = MyVirtualFileListener(editorHighlighterUpdater, textEditor)
    val fileSystem = file.fileSystem
    fileSystem.addVirtualFileListener(virtualFileListener)
    val documentListener = MyDocumentListener(textEditor)
    editor.document.addDocumentListener(documentListener)
    asyncLoader.coroutineScope.coroutineContext.job.invokeOnCompletion {
      fileSystem.removeVirtualFileListener(virtualFileListener)
      editor.document.removeDocumentListener(documentListener)
    }

    messageBusConnection.subscribe(FileTypeManager.TOPIC, MyFileTypeListener(textEditor))
    ApplicationManager.getApplication().invokeLater {
      if (!isDisposed) {
        updateModifiedProperty(textEditor)
      }
    }
  }

  final override fun add(comp: Component): Component {
    throw IllegalCallerException()
  }

  final override fun add(comp: Component, constraints: Any) {
    throw IllegalCallerException()
  }

  @Suppress("FunctionName", "unused")
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
   * editors, unregisters listeners. The behavior of the splitter after disposing is unpredictable.
   */
  open fun dispose() {
    EditorFactory.getInstance().releaseEditor(editorImpl)
    isDisposed = true
  }

  /**
   * @return most recently used editor
   */
  val editor: EditorEx
    get() = editorImpl

  /**
   * Just calculates "modified" property
   */
  private val isModifiedImpl: Boolean
    get() = FileDocumentManager.getInstance().isFileModified(file)

  /**
   * Updates "modified" property and fires event if necessary
   */
  fun updateModifiedProperty(textEditor: TextEditorImpl) {
    val oldModified = isModified
    isModified = isModifiedImpl
    textEditor.firePropertyChange(FileEditor.getPropModified(), oldModified, isModified)
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

  private fun updateValidProperty(textEditor: TextEditorImpl) {
    val oldValid = isValid
    isValid = isEditorValidImpl
    textEditor.firePropertyChange(FileEditor.getPropValid(), oldValid, isValid)
  }

  override fun uiDataSnapshot(sink: DataSink) {
    if (editorImpl.isDisposed) return
    sink[CommonDataKeys.EDITOR] = editorImpl
    sink[CommonDataKeys.CARET] = editorImpl.caretModel.currentCaret
    sink[CommonDataKeys.VIRTUAL_FILE] = file
  }

  /**
   * Updates "modified" property
   */
  private inner class MyDocumentListener(private val textEditor: TextEditorImpl) : DocumentListener {
    /**
     * We can reuse this runnable to decrease the number of allocated objects.
     */
    private val updateRunnable = Runnable {
      isUpdateScheduled = false
      updateModifiedProperty(textEditor)
    }

    private var isUpdateScheduled = false

    override fun documentChanged(e: DocumentEvent) {
      if (!isUpdateScheduled) {
        // a document's timestamp is changed later on undo or PSI changes
        ApplicationManager.getApplication().invokeLater(updateRunnable)
        isUpdateScheduled = true
      }
    }
  }

  private inner class MyFileTypeListener(private val textEditor: TextEditorImpl) : FileTypeListener {
    override fun fileTypesChanged(event: FileTypeEvent) {
      // File can be invalid after file type changing. The editor should be removed by the FileEditorManager if it's invalid.
      updateValidProperty(textEditor)
    }
  }

  /**
   * Updates "valid" property and highlighters (if necessary)
   */
  private inner class MyVirtualFileListener(
    private val editorHighlighterUpdater: EditorHighlighterUpdater,
    private val textEditor: TextEditorImpl,
  ) : VirtualFileListener {
    override fun propertyChanged(e: VirtualFilePropertyEvent) {
      if (e.propertyName == VirtualFile.PROP_NAME) {
        // File can be invalidated after file changes name (an extension also can change). The editor should be removed if it's invalid.
        updateValidProperty(textEditor)
        if (e.file == file && (FileContentUtilCore.FORCE_RELOAD_REQUESTOR == e.requestor || e.oldValue != e.newValue)) {
          editorHighlighterUpdater.updateHighlighters()
        }
      }
    }

    override fun contentsChanged(event: VirtualFileEvent) {
      // commit
      if (event.isFromSave) {
        ThreadingAssertions.assertEventDispatchThread()
        val file = event.file
        LOG.assertTrue(file.isValid)
        if (file == this@TextEditorComponent.file) {
          updateModifiedProperty(textEditor)
        }
      }
    }
  }

  // Swing calls us _before_ mine constructor
  @Suppress("UNNECESSARY_SAFE_CALL", "USELESS_ELVIS")
  override fun getBackground(): Color? = editorImpl?.backgroundColor ?: super.getBackground()

  override fun getComponentGraphics(g: Graphics): Graphics = JBSwingUtilities.runGlobalCGTransform(this, super.getComponentGraphics(g))
}