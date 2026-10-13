// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorOpenMode
import com.intellij.openapi.fileEditor.FileEditorOpenRequest
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorComposite
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorNavigatable
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.EditorComposite
import com.intellij.openapi.fileEditor.impl.EditorOpenTracker
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.EditorsSplitters
import com.intellij.openapi.fileEditor.impl.FileEditorOpenOptions
import com.intellij.openapi.fileEditor.impl.launchEditorOpenFuture
import com.intellij.openapi.fileEditor.impl.selectPreferredTextEditor
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.performWhenLoaded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.docking.DockContainer
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.CompletableFuture
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import javax.swing.JComponent

@Suppress("OVERRIDE_DEPRECATION")
abstract class FileEditorManagerEx : FileEditorManager() {
  companion object {
    @JvmStatic
    fun getInstanceEx(project: Project): FileEditorManagerEx = getInstance(project) as FileEditorManagerEx

    suspend fun getInstanceExAsync(project: Project): FileEditorManagerEx = project.serviceAsync<FileEditorManager>() as FileEditorManagerEx

    fun getInstanceExIfCreated(project: Project): FileEditorManagerEx? {
      return project.serviceIfCreated<FileEditorManager>() as FileEditorManagerEx?
    }
  }

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

  /**
   * Close editors opened in particular windows after running pre-close checks.
   * @return true if all requested editors were closed; false otherwise
   */
  @Internal
  @RequiresEdt
  open fun closeFilesWithChecks(filesWithWindows: List<Pair<EditorComposite, EditorWindow>>): Boolean {
    for (fileWithWindow in filesWithWindows) {
      if (!closeFileWithChecks(fileWithWindow.first.file, fileWithWindow.second)) {
        return false
      }
    }
    return true
  }

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

  override fun canOpenFile(file: VirtualFile): Boolean {
    return FileEditorProviderManager.getInstance().getProviderList(project, file).isNotEmpty()
  }

  @Internal
  open suspend fun canOpenFileAsync(file: VirtualFile): Boolean {
    return serviceAsync<FileEditorProviderManager>().getProvidersAsync(project, file).isNotEmpty()
  }

  final override fun getSelectedEditor(file: VirtualFile): FileEditor? = getSelectedEditorWithProvider(file)?.fileEditor

  abstract fun getSelectedEditorWithProvider(file: VirtualFile): FileEditorWithProvider?

  /**
   * Closes all files in the active splitter (window).
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

  abstract val splitters: EditorsSplitters

  @get:RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  open val activeSplittersComposites: List<EditorComposite>
    get() = splitters.getAllComposites()

  override fun openFile(file: VirtualFile, focusEditor: Boolean): Array<FileEditor> {
    return openFile(file = file, window = null, options = FileEditorOpenOptions(requestFocus = focusEditor))
      .allEditors
      .toTypedArray()
  }

  /**
   * Submits an open request without waiting and without requesting focus.
   */
  @ApiStatus.Experimental
  final override fun requestOpenFile(file: VirtualFile): CompletableFuture<FileEditorComposite> {
    return requestOpenFile(file, FileEditorOpenRequest.defaults())
  }

  @ApiStatus.Experimental
  override fun requestOpenFile(file: VirtualFile, request: FileEditorOpenRequest): CompletableFuture<FileEditorComposite> =
    requestOpenFile(file, buildFileEditorOpenOptions(resolveOpenMode(request), waitForCompositeOpen = false))

  /**
   * Opens in [window] ignoring the input event.
   * The request must use [FileEditorOpenMode.MANAGED] or [FileEditorOpenMode.DEFAULT].
   * A disposed window uses the normal window selection.
   * Remote Development managers ignore the local window placement.
   */
  @ApiStatus.Experimental
  fun requestOpenFileInWindow(file: VirtualFile, window: EditorWindow, request: FileEditorOpenRequest): CompletableFuture<FileEditorComposite> {
    require(request.openMode == FileEditorOpenMode.MANAGED || request.openMode == FileEditorOpenMode.DEFAULT) {
      "An explicit window requires MANAGED or DEFAULT placement"
    }
    require(window.manager === this) { "The window belongs to another editor manager" }
    return requestOpenFile(
      file,
      options = buildFileEditorOpenOptions(request, waitForCompositeOpen = false).copy(window = window)
    )
  }

  /**
   * Opens in [window] and waits for the composite. The request must use [FileEditorOpenMode.MANAGED] or [FileEditorOpenMode.DEFAULT].
   * A disposed window uses the normal window selection. Remote Development managers ignore the local window placement.
   */
  @ApiStatus.Experimental
  suspend fun openFileInWindow(file: VirtualFile, window: EditorWindow, request: FileEditorOpenRequest): FileEditorComposite {
    require(request.openMode == FileEditorOpenMode.MANAGED || request.openMode == FileEditorOpenMode.DEFAULT) {
      "An explicit window requires MANAGED or DEFAULT placement"
    }
    require(window.manager === this) { "The window belongs to another editor manager" }
    return openFile(
      file,
      options = buildFileEditorOpenOptions(request).copy(window = window)
    )
  }

  @ApiStatus.Experimental
  override suspend fun openFile(file: VirtualFile, request: FileEditorOpenRequest): FileEditorComposite =
    openFile(file, buildFileEditorOpenOptions(request))

  @ApiStatus.Experimental
  override suspend fun openTextEditor(descriptor: OpenFileDescriptor, request: FileEditorOpenRequest): Editor? {
    val composite = openEditor(descriptor, request)
    return withContext(Dispatchers.EDT) {
      selectPreferredTextEditor(composite, null)?.editor
    }
  }

  private fun requestOpenFile(file: VirtualFile, options: FileEditorOpenOptions): CompletableFuture<FileEditorComposite> {
    return EditorOpenTracker.getInstance(project).launchEditorOpenFuture(submitContext()) {
      val composite = withContext(Dispatchers.EDT) {
        openFile(file = file, window = null, options = options)
      }
      if (composite is EditorComposite) {
        composite.waitForAvailable()
        // the empty composite closes itself
        if (composite.providerSequence.any()) composite else FileEditorComposite.EMPTY
      }
      else {
        composite
      }
    }
  }

  @ApiStatus.Experimental
  override fun requestOpenEditor(descriptor: FileEditorNavigatable, request: FileEditorOpenRequest): CompletableFuture<FileEditorComposite> {
    val resolvedRequest = resolveOpenMode(request)
    return EditorOpenTracker.getInstance(project).launchEditorOpenFuture(submitContext()) {
      openEditor(descriptor, resolvedRequest)
    }
  }

  @ApiStatus.Experimental
  override fun requestOpenTextEditor(descriptor: OpenFileDescriptor, request: FileEditorOpenRequest): CompletableFuture<Editor?> {
    val resolvedRequest = resolveOpenMode(request)
    return EditorOpenTracker.getInstance(project).launchEditorOpenFuture(submitContext()) {
      openTextEditor(descriptor, resolvedRequest)
    }
  }

  private fun submitContext() = ModalityState.defaultModalityState().asContextElement() + ClientId.coroutineContext()

  /**
   * Opens the file, waits for the composite, and navigates the first editor that accepts the descriptor.
   * NB: the returned composite is available, but a text editor inside it may still be loading.
   *
   * For external plugins use [openEditor] or [openTextEditor].
   */
  @Internal
  open suspend fun openInEditor(
    descriptor: FileEditorNavigatable,
    focusEditor: Boolean,
    openMode: FileEditorOpenMode = FileEditorOpenMode.DEFAULT,
  ): FileEditorComposite {
    return openEditor(descriptor, FileEditorOpenRequest.fromDescriptor(descriptor)
      .withRequestFocus(focusEditor).withOpenMode(openMode))
  }

  /**
   * For external plugins use [requestOpenFile] or [openFile] with [FileEditorOpenRequest] parameter.
   *
   * The [window] provided by call sites that already hold a resolved window.
   * It's processed first, then one from [options]
   *
   * Implementations must keep this overload and the suspending [openFile] semantically aligned;
   * the synchronous variant is planned to become a facade over the suspending one (IJPL-247133).
   */
  @Internal
  abstract fun openFile(
    file: VirtualFile,
    window: EditorWindow?,
    options: FileEditorOpenOptions = FileEditorOpenOptions(),
  ): FileEditorComposite

  /**
   * Preloads the document before opening the file.
   * Uses the current progress or creates a cancellable background task when loading is needed.
   *
   * For external plugins use [requestOpenFile] or [openFile] with [FileEditorOpenRequest] parameter.
   *
   * Implementations must keep this overload and the synchronous [openFile] semantically aligned;
   * the synchronous variant is planned to become a facade over the suspending one (IJPL-247133).
   */
  @Internal
  abstract suspend fun openFile(file: VirtualFile, options: FileEditorOpenOptions = FileEditorOpenOptions()): FileEditorComposite

  /**
   * Legacy synchronous API.
   * Prefer [requestOpenFile] if editor instances are not needed immediately,
   * or [openFile] from coroutine code.
   */
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

  abstract fun isChanged(editor: EditorComposite): Boolean

  abstract fun getNextWindow(window: EditorWindow): EditorWindow?

  abstract fun getPrevWindow(window: EditorWindow): EditorWindow?

  open fun updateFileName(file: VirtualFile) {}

  open fun refreshIcons() {}

  abstract fun getSplittersFor(component: Component): EditorsSplitters?

  abstract fun notifyPublisher(runnable: Runnable)

  override fun runWhenLoaded(editor: Editor, runnable: Runnable) {
    performWhenLoaded(editor, runnable)
  }

  @ApiStatus.Experimental
  final override suspend fun awaitLoaded(editor: Editor) {
    AsyncEditorLoader.awaitLoaded(editor)
  }

  @Internal
  open suspend fun waitForTextEditors() {
  }
}
