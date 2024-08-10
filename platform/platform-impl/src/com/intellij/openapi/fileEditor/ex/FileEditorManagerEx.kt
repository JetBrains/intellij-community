// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.ide.impl.DataValidators
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.performWhenLoaded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.docking.DockContainer
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.awt.Component
import java.util.concurrent.CompletableFuture
import javax.swing.JComponent

abstract class FileEditorManagerEx : FileEditorManager() {
  companion object {
    @JvmStatic
    @RequiresBlockingContext
    fun getInstanceEx(project: Project): FileEditorManagerEx = getInstance(project) as FileEditorManagerEx

    fun getInstanceExIfCreated(project: Project): FileEditorManagerEx? {
      return project.serviceIfCreated<FileEditorManager>() as FileEditorManagerEx?
    }
  }

  private val dataProviders = ArrayList<EditorDataProvider>()

  open val dockContainer: DockContainer?
    get() = null

  /**
   * @return `JComponent` which represent the place where all editors are located
   */
  abstract val component: JComponent?

  /**
   * @return preferred focused component inside myEditor tabbed container.
   * This method does similar things like [FileEditor.getPreferredFocusedComponent]
   * but it also tracks (and remember) focus movement inside tabbed container.
   *
   * @see EditorComposite.preferredFocusedComponent
   */
  abstract val preferredFocusedComponent: JComponent?

  abstract fun getEditorsWithProviders(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>>

  /**
   * Synchronous version of [.getActiveWindow]. Will return `null` if invoked not from EDT.
   * @return current window in splitters
   */
  abstract var currentWindow: EditorWindow?

  /**
   * Asynchronous version of [.getCurrentWindow]. Execution happens after focus settles down. Can be invoked on any thread.
   */
  abstract val activeWindow: CompletableFuture<EditorWindow?>

  /**
   * Close editors for the file opened in a particular window.
   * @param file file to be closed. Cannot be null.
   */
  abstract fun closeFile(file: VirtualFile, window: EditorWindow)

  /**
   * Close editors for the file opened in a particular window.
   * This method runs some checks before closing the window.
   * E.g., confirmation dialog that can prevent the window from closing
   * @param file file to be closed. Cannot be null.
   * @return true if the window was closed; false otherwise
   */
  abstract fun closeFileWithChecks(file: VirtualFile, window: EditorWindow): Boolean

  abstract fun unsplitAllWindow()

  abstract val windowSplitCount: Int

  abstract fun hasSplitOrUndockedWindows(): Boolean

  abstract val windows: Array<EditorWindow>

  /**
   * @return arrays of all files (including `file` itself) that belong to the same tabbed container.
   * The method returns an empty array if `file` is not open. The returned files have the same order as they have in the tabbed container.
   */
  abstract fun getSiblings(file: VirtualFile): Collection<VirtualFile>

  abstract fun createSplitter(orientation: Int, window: EditorWindow?)

  abstract fun changeSplitterOrientation()

  abstract val isInSplitter: Boolean

  abstract fun hasOpenedFile(): Boolean

  @RequiresBlockingContext
  open fun canOpenFile(file: VirtualFile): Boolean {
    return FileEditorProviderManager.getInstance().getProviderList(project, file).isNotEmpty()
  }

  abstract val currentFile: VirtualFile?

  final override fun getSelectedEditor(file: VirtualFile): FileEditor? = getSelectedEditorWithProvider(file)?.fileEditor

  abstract fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider?

  /**
   * Closes all files in active splitter (window).
   * @see com.intellij.ui.docking.DockManager.getContainers
   * @see com.intellij.ui.docking.DockContainer.closeAll
   */
  abstract fun closeAllFiles()

  /**
   * Closes all editors in all windows.
   */
  open fun closeOpenedEditors() {
    closeAllFiles()
  }

  abstract val currentFileEditorFlow: StateFlow<FileEditor?>

  abstract val splitters: EditorsSplitters

  @get:RequiresEdt
  open val activeSplittersComposites: List<EditorComposite>
    get() = splitters.getAllComposites()

  override fun openFile(file: VirtualFile, focusEditor: Boolean): Array<FileEditor> {
    return openFile(file = file, window = null, options = FileEditorOpenOptions(requestFocus = focusEditor))
      .allEditors
      .toTypedArray()
  }

  final override fun requestOpenFile(file: VirtualFile) {
    openFile(file = file, window = null, options = FileEditorOpenOptions(waitForCompositeOpen = false))
  }

  final override fun openFile(file: VirtualFile): List<FileEditor> {
    return openFile(file = file, window = null, options = FileEditorOpenOptions(requestFocus = false)).allEditors
  }

  final override fun openFile(file: VirtualFile, focusEditor: Boolean, searchForOpen: Boolean): Array<FileEditor> {
    return openFile(
      file = file,
      window = null,
      options = FileEditorOpenOptions(requestFocus = focusEditor, reuseOpen = searchForOpen),
    )
      .allEditors
      .toTypedArray()
  }

  @Deprecated(message = "Use openFile()", ReplaceWith("openFile(file, window, options)"), level = DeprecationLevel.ERROR)
  fun openFileWithProviders(file: VirtualFile,
                            focusEditor: Boolean,
                            searchForSplitter: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    val openOptions = FileEditorOpenOptions(requestFocus = focusEditor, reuseOpen = searchForSplitter)
    return openFile(file = file, window = null, options = openOptions).retrofit()
  }

  @Deprecated(message = "Use openFile()", ReplaceWith("openFile(file, window, options)"), level = DeprecationLevel.ERROR)
  fun openFileWithProviders(file: VirtualFile,
                            focusEditor: Boolean,
                            window: EditorWindow): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFile(file = file, window = window, options = FileEditorOpenOptions(requestFocus = focusEditor)).retrofit()
  }

  abstract fun openFile(file: VirtualFile,
                        window: EditorWindow?,
                        options: FileEditorOpenOptions = FileEditorOpenOptions()): FileEditorComposite

  @Experimental
  abstract suspend fun openFile(file: VirtualFile, options: FileEditorOpenOptions = FileEditorOpenOptions()): FileEditorComposite

  abstract fun isChanged(editor: EditorComposite): Boolean

  abstract fun getNextWindow(window: EditorWindow): EditorWindow?

  abstract fun getPrevWindow(window: EditorWindow): EditorWindow?

  /** @deprecated Use [com.intellij.openapi.actionSystem.UiDataRule] instead */
  @Deprecated("Use [UiDataRule] instead", level = DeprecationLevel.ERROR)
  override fun getData(dataId: String, editor: Editor, caret: Caret): Any? {
    for (dataProvider in dataProviders) {
      val o = dataProvider.getData(dataId, editor, caret) ?: continue
      return DataValidators.validOrNull(o, dataId, dataProvider)
    }
    return null
  }

  /** @deprecated Use [com.intellij.openapi.actionSystem.UiDataRule] instead */
  @Deprecated("Use [UiDataRule] instead", level = DeprecationLevel.ERROR)
  override fun registerExtraEditorDataProvider(provider: EditorDataProvider, parentDisposable: Disposable?) {
    dataProviders.add(provider)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) { dataProviders.remove(provider) }
    }
  }

  @Deprecated("Drop together with [registerExtraEditorDataProvider]")
  internal class DataRule : UiDataRule {
    override fun uiDataSnapshot(sink: DataSink, snapshot: DataSnapshot) {
      val project = snapshot[PlatformDataKeys.PROJECT] ?: return
      val caret = snapshot[PlatformDataKeys.CARET] ?: return
      getInstanceEx(project).dataProviders.forEach { provider ->
        DataSink.uiDataSnapshot(sink, object : DataProvider, DataValidators.SourceWrapper {
          override fun getData(dataId: @NonNls String): Any? = provider.getData(dataId, caret.editor, caret)
          override fun unwrapSource(): Any = provider
        })
      }
    }
  }

  open fun refreshIcons() {}

  abstract fun getSplittersFor(component: Component): EditorsSplitters?

  abstract fun notifyPublisher(runnable: Runnable)

  override fun runWhenLoaded(editor: Editor, runnable: Runnable) {
    performWhenLoaded(editor, runnable)
  }

  @Internal
  @Experimental
  open suspend fun waitForTextEditors() {
  }
}