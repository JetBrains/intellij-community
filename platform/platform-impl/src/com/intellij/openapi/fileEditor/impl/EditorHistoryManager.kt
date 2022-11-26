// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "editorHistoryManager", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)])
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
        updateHistoryEntry(file, false)
      }
    })
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, MyEditorManagerListener())
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
    fun getInstance(project: Project): EditorHistoryManager = project.service()
  }

  internal class EditorHistoryManagerStartUpActivity : ProjectPostStartupActivity {
    override suspend fun execute(project: Project) {
      getInstance(project)
    }
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
  interface IncludeInEditorHistoryFile

  /**
   * Makes file most recent one
   */
  private fun fileOpenedImpl(file: VirtualFile, fallbackEditor: FileEditor?, fallbackProvider: FileEditorProvider?) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    // don't add files that cannot be found via VFM (light & etc.)
    if (file !is IncludeInEditorHistoryFile && VirtualFileManager.getInstance().findFileByUrl(file.url) == null) {
      return
    }

    val editorManager = FileEditorManagerEx.getInstanceEx(project)
    val editorComposite = editorManager.getComposite(file)
    var editors = editorComposite?.allEditors ?: emptyList()
    var oldProviders = editorComposite?.allProviders ?: emptyList()
    LOG.assertTrue(editors.size == oldProviders.size, "Different number of editors and providers")
    if (editors.isEmpty() && fallbackEditor != null && fallbackProvider != null) {
      editors = listOf(fallbackEditor)
      oldProviders = listOf(fallbackProvider)
    }
    if (editors.isEmpty()) {
      // fileOpened notification is asynchronous, file could have been closed by now due to some reason
      return
    }

    val selectedEditor = editorManager.getSelectedEditor(file) ?: fallbackEditor
    LOG.assertTrue(selectedEditor != null)
    val selectedProviderIndex = editors.indexOf(selectedEditor)
    LOG.assertTrue(selectedProviderIndex != -1, "Can't find $selectedEditor among $editors")

    getEntry(file)?.let {
      moveOnTop(it)
      return
    }

    val states = arrayOfNulls<FileEditorState>(editors.size)
    val providers = arrayOfNulls<FileEditorProvider>(editors.size)
    for (i in editors.indices.reversed()) {
      providers[i] = oldProviders[i]
      val editor = editors[i]
      if (editor.isValid) {
        states[i] = editor.getState(FileEditorStateLevel.FULL)
      }
    }

    synchronized(this) {
      entries.add(
        HistoryEntry.createHeavy(project, file, providers.asList(), states.asList(), providers[selectedProviderIndex]!!,
                                 editorComposite != null && editorComposite.isPreview))
    }
    trimToSize()
  }

  fun updateHistoryEntry(file: VirtualFile, changeEntryOrderOnly: Boolean) {
    updateHistoryEntry(file, null, null, changeEntryOrderOnly)
  }

  private fun updateHistoryEntry(file: VirtualFile,
                                 fileEditor: FileEditor?,
                                 fileEditorProvider: FileEditorProvider?,
                                 changeEntryOrderOnly: Boolean) {
    val editorManager = FileEditorManagerEx.getInstanceEx(project)
    val editors: List<FileEditor>
    val providers: List<FileEditorProvider>
    var preview = false
    if (fileEditor == null || fileEditorProvider == null) {
      val composite = editorManager.getComposite(file)
      editors = composite?.allEditors ?: emptyList()
      providers = composite?.allProviders ?: emptyList()
      preview = composite != null && composite.isPreview
    }
    else {
      editors = listOf(fileEditor)
      providers = listOf(fileEditorProvider)
    }
    if (editors.isEmpty()) {
      // obviously not opened in any editor at the moment,
      // makes no sense to put the file in the history
      return
    }
    val entry = getEntry(file)
    if (entry == null) {
      // Size of entry list can be less than number of opened editors (some entries can be removed)
      if (file.isValid) {
        // the file could have been deleted, so the isValid() check is essential
        fileOpenedImpl(file, fileEditor, fileEditorProvider)
      }
      return
    }
    if (!changeEntryOrderOnly) {
      // update entry state
      for (i in editors.indices.reversed()) {
        val editor = editors[i]
        val provider = providers[i]
        // can happen if fileEditorProvider is null
        if (!editor.isValid) {
          // this can happen for example if file extension was changed
          // and this method was called during corresponding myEditor close up
          continue
        }
        val oldState = entry.getState(provider)
        val newState = editor.getState(FileEditorStateLevel.FULL)
        if (newState != oldState) {
          entry.putState(provider, newState)
        }
      }
    }
    val selectedEditorWithProvider = editorManager.getSelectedEditorWithProvider(file)
    if (selectedEditorWithProvider != null) {
      //LOG.assertTrue(selectedEditorWithProvider != null);
      entry.selectedProvider = selectedEditorWithProvider.provider
      LOG.assertTrue(entry.selectedProvider != null)
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

  fun getState(file: VirtualFile, provider: FileEditorProvider): FileEditorState? {
    return getEntry(file)?.getState(provider)
  }

  /**
   * @return may be null
   */
  fun getSelectedProvider(file: VirtualFile): FileEditorProvider? {
    return getEntry(file)?.selectedProvider
  }

  @Synchronized
  private fun getEntry(file: VirtualFile): HistoryEntry? {
    for (i in entries.indices.reversed()) {
      val entry = entries[i]
      val entryFile = entry.file
      if (file == entryFile) {
        return entry
      }
    }
    return null
  }

  /**
   * If total number of files in history more than `UISettings.RECENT_FILES_LIMIT`
   * then removes the oldest ones to fit the history to new size.
   */
  @Synchronized
  private fun trimToSize() {
    val limit = getInstance().recentFilesLimit + 1
    while (entries.size > limit) {
      entries.removeAt(0).destroy()
    }
  }

  @Synchronized
  override fun loadState(state: Element) {
    // each HistoryEntry contains myDisposable that must be disposed to dispose corresponding virtual file pointer
    removeAllFiles()

    // backward compatibility - previously entry maybe duplicated
    val fileToElement: MutableMap<String, Element> = LinkedHashMap()
    for (e in state.getChildren(HistoryEntry.TAG)) {
      val file = e.getAttributeValue(HistoryEntry.FILE_ATTR)
      fileToElement.remove(file)
      // last is the winner
      fileToElement[file] = e
    }
    for (e in fileToElement.values) {
      try {
        entries.add(HistoryEntry.createHeavy(project, e))
      }
      catch (ignored: ProcessCanceledException) {
      }
      catch (anyException: Exception) {
        LOG.error(anyException)
      }
    }
  }

  @Synchronized
  override fun getState(): Element {
    val element = Element("state")
    // update history before saving
    val openFiles = FileEditorManager.getInstance(project).openFiles
    for (i in openFiles.indices.reversed()) {
      val file = openFiles[i]
      // we have to update only files that are in history
      if (getEntry(file) != null) {
        updateHistoryEntry(file, false)
      }
    }
    for (entry in entries) {
      entry.writeExternal(element, project)
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
  private inner class MyEditorManagerListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
      fileOpenedImpl(file = file, fallbackEditor = null, fallbackProvider = null)
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
      // updateHistoryEntry does commitDocument which is 1) very expensive and 2) cannot be performed from within PSI change listener
      // so defer updating history entry until documents committed to improve responsiveness
      PsiDocumentManager.getInstance(project).performWhenAllCommitted {
        val newEditor = event.newEditor
        if (newEditor != null && !newEditor.isValid) {
          return@performWhenAllCommitted
        }

        val oldFile = event.oldFile
        if (oldFile != null) {
          updateHistoryEntry(file = oldFile,
                             fileEditor = event.oldEditor,
                             fileEditorProvider = event.oldProvider,
                             changeEntryOrderOnly = false)
        }
        val newFile = event.newFile
        if (newFile != null) {
          updateHistoryEntry(file = newFile, changeEntryOrderOnly = true)
        }
      }
    }
  }
}