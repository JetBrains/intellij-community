// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.pointers.VirtualFilePointerManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting

@Service(Service.Level.PROJECT)
@State(name = "editorHistoryManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], getStateRequiresEdt = true)
class EditorHistoryManager internal constructor(private val project: Project) : PersistentStateComponent<Element?>, Disposable {
  /**
   * State corresponding to the most recent file is the last
   */
  private val entries = ArrayList<HistoryEntry>()

  init {
    val connection = project.messageBus.simpleConnect()
    connection.subscribe(UISettingsListener.TOPIC, UISettingsListener { trimToSize() })
    connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, object : FileEditorManagerListener.Before {
      override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) {
        updateHistoryEntry(
          fileEditorManager = source as FileEditorManagerEx,
          file = file,
          fallback = null,
          changeEntryOrderOnly = false,
        )
      }
    })
    FileEditorProvider.EP_FILE_EDITOR_PROVIDER.addExtensionPointListener(object : ExtensionPointListener<FileEditorProvider> {
      override fun extensionRemoved(extension: FileEditorProvider, pluginDescriptor: PluginDescriptor) {
        for (it in entries) {
          it.onProviderRemoval(extension)
        }
      }
    }, this)
  }

  companion object {
    private val LOG = logger<EditorHistoryManager>()

    @JvmStatic
    @RequiresBlockingContext
    fun getInstance(project: Project): EditorHistoryManager = project.service()
  }

  @Synchronized
  private fun removeEntry(entry: HistoryEntry) {
    if (entries.remove(entry)) {
      entry.destroy()
    }
  }

  @Synchronized
  private fun moveOnTop(entry: HistoryEntry) {
    entries.remove(entry)
    entries.add(entry)
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  interface IncludeInEditorHistoryFile : OptionallyIncluded {
    override fun isIncludedInEditorHistory(project: Project): Boolean = true
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  interface OptionallyIncluded {
    fun isIncludedInEditorHistory(project: Project): Boolean
  }

  private fun isIncludedInHistory(file: VirtualFile): Boolean {
    if (file is OptionallyIncluded) return file.isIncludedInEditorHistory(project)

    // don't add files that cannot be found via VFM (light & etc.)
    return VirtualFileManager.getInstance().findFileByUrl(file.url) != null
  }

  /**
   * Makes file the most recent one
   */
  private fun fileOpenedImpl(
    file: VirtualFile,
    fallback: FileEditorWithProvider?,
    fileEditorManager: FileEditorManagerEx,
  ) {
    ThreadingAssertions.assertEventDispatchThread()

    if (!isIncludedInHistory(file)) {
      return
    }

    val editorComposite = fileEditorManager.getComposite(file)
    var list = editorComposite?.allEditorsWithProviders ?: emptyList()
    if (list.isEmpty()) {
      list = listOfNotNull(fallback)
      if (list.isEmpty()) {
        // fileOpened notification is asynchronous, file could have been closed by now due to some reason
        return
      }
    }

    val selected = if (editorComposite is EditorComposite) {
      editorComposite.selectedWithProvider ?: list.first()
    }
    else {
      val selected = fileEditorManager.getSelectedEditorWithProvider(file) ?: fallback
      val selectedProviderIndex = selected?.let { list.indexOf(it) } ?: -1
      if (selectedProviderIndex == -1) {
        LOG.error("Can't find $selected among $list")
        list.first()
      }
      else {
        list.get(selectedProviderIndex)
      }
    }

    getEntry(file)?.let {
      moveOnTop(it)
      return
    }

    val stateMap = LinkedHashMap<FileEditorProvider, FileEditorState>(list.size)
    for (item in list) {
      val state = item.fileEditor.takeIf { it.isValid }?.getState(FileEditorStateLevel.FULL) ?: continue
      stateMap.put(item.provider, state)
    }

    val disposable = Disposer.newDisposable()
    val pointer = VirtualFilePointerManager.getInstance().create(file, disposable, null)
    val entry = HistoryEntry(
      filePointer = pointer,
      selectedProvider = selected.provider,
      isPreview = editorComposite != null && editorComposite.isPreview,
      disposable = disposable,
      providerToState = stateMap,
    )
    synchronized(this) {
      entries.add(entry)
      trimToSize()
    }
  }

  fun updateHistoryEntry(file: VirtualFile, changeEntryOrderOnly: Boolean) {
    updateHistoryEntry(
      fileEditorManager = FileEditorManagerEx.getInstanceEx(project),
      file = file,
      fallback = null,
      changeEntryOrderOnly = changeEntryOrderOnly,
    )
  }

  private fun updateHistoryEntry(
    fileEditorManager: FileEditorManagerEx,
    file: VirtualFile,
    fallback: FileEditorWithProvider?,
    changeEntryOrderOnly: Boolean,
  ) {
    val list: List<FileEditorWithProvider>
    var preview = false
    if (fallback == null) {
      val composite = fileEditorManager.getComposite(file) ?: return
      list = composite.allEditorsWithProviders
      preview = composite.isPreview
    }
    else {
      list = listOf(fallback)
    }

    if (list.isEmpty()) {
      // not opened in any editor at the moment makes no sense to put the file in the history
      return
    }

    val entry = getEntry(file)
    if (entry == null) {
      // The size of an entry list can be less than the number of opened editors (some entries can be removed)
      if (file.isValid) {
        // the file could have been deleted, so the isValid() check is essential
        fileOpenedImpl(
          file = file,
          fallback = fallback,
          fileEditorManager = fileEditorManager,
        )
      }
      return
    }

    if (!changeEntryOrderOnly) {
      // update entry state
      for ((editor, provider) in list.reversed()) {
        // can happen if fileEditorProvider is null
        if (!editor.isValid) {
          // this can happen, for example, if a file extension was changed
          // and this method was called during the corresponding myEditor close up
          continue
        }

        val oldState = entry.getState(provider)
        val newState = editor.getState(FileEditorStateLevel.FULL)
        if (newState != oldState) {
          entry.putState(provider, newState)
        }
      }
    }

    val selectedEditorWithProvider = fileEditorManager.getSelectedEditorWithProvider(file)
    if (selectedEditorWithProvider != null) {
      entry.selectedProvider = selectedEditorWithProvider.provider
      if (changeEntryOrderOnly) {
        moveOnTop(entry)
      }
    }
    if (preview) {
      entry.isPreview = true
    }
  }

  /**
   * @return array of valid files that are in the history, the oldest first.
   */
  @get:Synchronized
  val files: Array<VirtualFile>
    get() = VfsUtilCore.toVirtualFileArray(entries.mapNotNullTo(ArrayList(entries.size)) { it.file })

  /**
   * For internal or test-only usage.
   */
  @VisibleForTesting
  @Synchronized
  fun removeAllFiles() {
    for (entry in entries) {
      entry.destroy()
    }
    entries.clear()
  }

  /**
   * @return a list of valid files that are in the history, the oldest first.
   */
  @get:Synchronized
  val fileList: List<VirtualFile>
    get() = entries.mapNotNull { it.file }

  @Synchronized
  fun hasBeenOpen(f: VirtualFile): Boolean = entries.any { f == it.file }

  /**
   * Removes specified `file` from history. The method does
   * nothing if `file` is not in the history.
   *
   * @exception IllegalArgumentException if `file`
   * is `null`
   */
  @Synchronized
  fun removeFile(file: VirtualFile) {
    getEntry(file)?.let { removeEntry(it) }
  }

  fun getState(file: VirtualFile, provider: FileEditorProvider): FileEditorState? = getEntry(file)?.getState(provider)

  fun getSelectedProvider(file: VirtualFile): FileEditorProvider? = getEntry(file)?.selectedProvider

  @Synchronized
  private fun getEntry(file: VirtualFile): HistoryEntry? = entries.lastOrNull { it.file == file  }

  /**
   * If total number of files in history more than `UISettings.RECENT_FILES_LIMIT`
   * then removes the oldest ones to fit the history to new size.
   */
  @Synchronized
  private fun trimToSize() {
    val limit = UISettings.getInstance().recentFilesLimit + 1
    while (entries.size > limit) {
      entries.removeAt(0).destroy()
    }
  }

  override fun loadState(state: Element) {
    // each HistoryEntry contains disposable that must be disposed to dispose a corresponding virtual file pointer
    removeAllFiles()

    // backward compatibility - previous entry maybe duplicated
    val fileToElement = LinkedHashMap<String, Element>()
    for (e in state.getChildren(HistoryEntry.TAG)) {
      val file = e.getAttributeValue(HistoryEntry.FILE_ATTRIBUTE)
      fileToElement.remove(file)
      // the last is the winner
      fileToElement.put(file, e)
    }

    val list = fileToElement.values.mapNotNull { element ->
      try {
        SlowOperations.knownIssue("IDEA-333919, EA-831462").use {
          HistoryEntry.createHeavy(project, element)
        }
      }
      catch (ignored: ProcessCanceledException) {
        null
      }
      catch (e: Exception) {
        LOG.error(e)
        null
      }
    }
    synchronized(this) {
      entries.addAll(list)
    }
  }

  @Synchronized
  override fun getState(): Element {
    val element = Element("state")
    // update history before saving
    val fileEditorManager = FileEditorManagerEx.getInstanceEx(project)
    for (file in fileEditorManager.openFiles.reversed()) {
      // we have to update only files that are in history
      if (getEntry(file) != null) {
        updateHistoryEntry(
          fileEditorManager = fileEditorManager,
          file = file,
          fallback = null,
          changeEntryOrderOnly = false,
        )
      }
    }
    for (entry in entries) {
      element.addContent(entry.writeExternal(project))
    }
    return element
  }

  @Synchronized
  override fun dispose() {
    removeAllFiles()
  }

  /**
   * Updates history
   */
  internal class MyEditorManagerListener(private val project: Project) : FileEditorManagerListener {
    private val service by lazy(LazyThreadSafetyMode.NONE) { getInstance(project) }

    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
      service.fileOpenedImpl(
        file = file,
        fallback = null,
        fileEditorManager = source as? FileEditorManagerEx ?: return,
      )
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
      // updateHistoryEntry does commitDocument, which is 1) costly and 2) cannot be performed from within PSI change listener
      // so defer updating history entry until documents are committed to improve responsiveness
      PsiDocumentManager.getInstance(project).performWhenAllCommitted {
        val newEditor = event.newEditor
        if (newEditor != null && !newEditor.isValid) {
          return@performWhenAllCommitted
        }

        val oldFile = event.oldFile
        if (oldFile != null) {
          service.updateHistoryEntry(
            fileEditorManager = event.manager as FileEditorManagerEx,
            file = oldFile,
            fallback = event.oldEditor?.let { FileEditorWithProvider(fileEditor = it, provider = event.oldProvider!!) },
            changeEntryOrderOnly = false,
          )
        }

        val newFile = event.newFile
        if (newFile != null) {
          service.updateHistoryEntry(
            fileEditorManager = event.manager as FileEditorManagerEx,
            file = newFile,
            fallback = event.newEditor?.let { FileEditorWithProvider(fileEditor = it, provider = event.newProvider!!) },
            changeEntryOrderOnly = true,
          )
        }
      }
    }
  }
}