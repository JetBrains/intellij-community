/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.reactiveidea

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorPsiDataProvider
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.impl.MessageListenerList
import com.intellij.util.ui.UIUtil
import com.jetbrains.reactivemodel.*
import com.jetbrains.reactivemodel.models.ListModel
import com.jetbrains.reactivemodel.models.MapModel
import com.jetbrains.reactivemodel.models.PrimitiveModel
import com.jetbrains.reactivemodel.util.get
import com.jetbrains.reactivemodel.util.host
import com.jetbrains.reactivemodel.util.lifetime
import java.awt.Component
import java.util.HashMap
import javax.swing.JComponent
import javax.swing.JPanel

public class ServerFileEditorManager(val myProject: Project) : FileEditorManagerEx(), ProjectComponent {
  companion object {
    private val EMPTY_EDITOR_ARRAY = arrayOf<FileEditor>()
    private val EMPTY_PROVIDER_ARRAY = arrayOf<FileEditorProvider>()

    private val LOG = Logger.getInstance("#com.jetbrains.reactiveidea.ServerFileEditorManager")
  }

  init {
    registerExtraEditorDataProvider(TextEditorPsiDataProvider(), null)
  }

  private volatile var myPanel: JPanel? = null
  private val myListenerList: MessageListenerList<FileEditorManagerListener>
  // one thread per model?
  private val modelEditors = HashMap<ReactiveModel, Signal<HashMap<VirtualFile, Pair<Array<FileEditor>, Array<FileEditorProvider>>>>>()

  private val model: ReactiveModel?
    get() = ReactiveModel.current()

  private val myVirtualFile2Editor: HashMap<VirtualFile, Pair<Array<FileEditor>, Array<FileEditorProvider>>>
    get() {
      val m = model ?: return HashMap()

      var signal = modelEditors.getOrPut(m, {
        val editorSignal = m.subscribe(m.lifetime, editorsTag)
        reaction(true, "editors reaction", editorSignal) { editors ->
          hashMapOf(*editors.map { editor ->
            val file = editor.meta.host<EditorHost>().file
            file to
                (Pair.create(arrayOf(editor.meta.host<EditorHost>().textEditor as FileEditor)
                    , FileEditorProviderManager.getInstance().getProviders(myProject, file)))
          }.toTypedArray())
        }
      })
      return signal.value
    }

  private val selectedEditorSignals = HashMap<ReactiveModel, Signal<Editor?>>()
  private val selectedEditor: Editor?
    get() {
      val m = model ?: return null

      var signal = selectedEditorSignals.getOrPut(m, {
        val editorSignal = m.subscribe(m.lifetime, editorsTag)
        val subs = reaction(true, "selected editor reaction", editorSignal) { editors ->
          editors.map { editor: MapModel ->
            val host = editor.meta.host<EditorHost>()
            val sub = m.subscribe(editor.meta.lifetime()!!, host.path / "selected")
            reaction(true, "sub resction", sub) {
              host.editor to it
            }
          }
        }
        var signalList = flatten(reaction(true, "editor reactions", subs) { list ->
          unlist(list)
        })

        val selSig = reaction(true, "selected editor reaction", signalList) { selections ->
          val selected = selections?.filter { value: kotlin.Pair<Editor, Model?> ->
            val isSelected = value.second as? PrimitiveModel<*>
            isSelected != null && (isSelected.value as Boolean)
          }?.map { value ->
            value.first
          } ?: emptyList()
          assert(selected.isEmpty() || selected.size() == 1)
          selected.firstOrNull()
        }
        var lastEditor: Editor? = null;
        val notNull = reaction(true, "not-nullize selected editor", selSig) {
          if (it != null) {
            lastEditor = it
            it
          }
          else lastEditor
        }

        reaction(false, "active editors change", notNull) { editor ->
          CommandProcessor.getInstance().executeCommand(myProject, object : Runnable {
            override fun run() {
              println("command $editor")
              (IdeDocumentHistory.getInstance(myProject) as IdeDocumentHistoryImpl).onSelectionChanged()
            }
          }, null, null)
        }

        selSig
      })
      return signal.value
    }


  init {
    myListenerList = MessageListenerList(myProject.getMessageBus(), FileEditorManagerListener.FILE_EDITOR_MANAGER)

    if (Extensions.getExtensions(FileEditorAssociateFinder.EP_NAME).size() > 0) {
      myListenerList.add(object : FileEditorManagerAdapter() {
        override fun selectionChanged(event: FileEditorManagerEvent) {
          openFile(event.getNewFile(), true)
        }
      })
    }
  }

  private fun assertReadAccess() {
    ApplicationManager.getApplication().assertReadAccessAllowed()
  }

  override fun getComponentName(): String {
    return FileEditorManagerImpl.FILE_EDITOR_MANAGER;
  }

  override fun initComponent() {
  }

  override fun disposeComponent() {
  }

  override fun projectOpened() {
  }

  override fun projectClosed() {
    // Dispose created editors. We do not use use closeEditor method because
    // it fires event and changes history.
    closeAllFiles()
  }

  override fun unsplitWindow() {

  }

  override fun getComponent(): JComponent? {
    initUI()
    return myPanel
  }

  override fun getPreferredFocusedComponent(): JComponent? {
    return null;
  }

  override fun getEditorsWithProviders(file: VirtualFile): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    assertReadAccess()
    return myVirtualFile2Editor[file] ?: Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY)
  }

  override fun getFile(editor: FileEditor): VirtualFile? {
    for (e in myVirtualFile2Editor) {
      if (e.value.first.contains(editor)) {
        return e.key
      }
    }
    return null
  }

  override fun updateFilePresentation(file: VirtualFile) {

  }

  override fun getCurrentWindow(): EditorWindow? = null

  override fun getActiveWindow(): AsyncResult<EditorWindow> {
    throw UnsupportedOperationException()
  }

  override fun setCurrentWindow(window: EditorWindow?) {
    if (window != null) {
      throw UnsupportedOperationException()
    }
  }

  override fun closeFile(file: VirtualFile, window: EditorWindow) {
    throw UnsupportedOperationException()
  }

  override fun unsplitAllWindow() {

  }

  override fun getWindowSplitCount(): Int = 0

  override fun hasSplitOrUndockedWindows(): Boolean = false

  override fun getWindows(): Array<out EditorWindow> {
    return emptyArray()
  }

  override fun getSiblings(file: VirtualFile): Array<out VirtualFile> {
    return getOpenFiles()
  }

  override fun createSplitter(orientation: Int, window: EditorWindow?) {
    // window was available from action event, for example when invoked from the tab menu of an editor that is not the 'current'
    if (window != null) {
      window.split(orientation, true, null, false);
    } else {
      // otherwise we'll split the current window, if any
      getSplitters().getCurrentWindow()?.split(orientation, true, null, false)
    }
  }

  override fun changeSplitterOrientation() {
    throw UnsupportedOperationException()
  }

  override fun flipTabs() {
  }

  override fun tabsMode(): Boolean {
    return false
  }

  override fun isInSplitter(): Boolean {
    return false
  }

  override fun hasOpenedFile(): Boolean {
    return myVirtualFile2Editor.isNotEmpty();
  }

  override fun getCurrentFile(): VirtualFile? {
    val editor = getSelectedTextEditor()
    if (editor is EditorImpl) {
      return editor.getVirtualFile()
    }
    return null
  }

  override fun getSelectedEditorWithProvider(file: VirtualFile): Pair<FileEditor, FileEditorProvider>? {
    val pairs = myVirtualFile2Editor[file] ?: return null
    return Pair.create(pairs.first.first(), pairs.second.first())
  }

  override fun closeAllFiles() {
  }

  private val myInitLock = Object()

  private fun initUI() {
    if (myPanel == null) {
      synchronized (myInitLock) {
        if (myPanel == null) {
          val panel = JPanel()
          panel.setOpaque(false)
          myPanel = panel
        }
      }
    }
  }

  override fun getSplitters(): EditorsSplitters {
    throw UnsupportedOperationException()
  }

  override fun openFileWithProviders(file: VirtualFile, focusEditor: Boolean, searchForSplitter: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileImpl(file, focusEditor)
  }

  override fun openFileWithProviders(file: VirtualFile, focusEditor: Boolean, window: EditorWindow): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    return openFileWithProviders(file, focusEditor, false)
  }

  override fun isChanged(editor: EditorComposite): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getNextWindow(window: EditorWindow): EditorWindow? = null

  override fun getPrevWindow(window: EditorWindow): EditorWindow? = null

  override fun isInsideChange(): Boolean = false

  override fun getSplittersFor(c: Component?): EditorsSplitters? = null

  private val myBusyObject = BusyObject.Impl.Simple()

  override fun notifyPublisher(runnable: Runnable): ActionCallback {
    val focusManager = IdeFocusManager.getInstance(myProject)
    val done = ActionCallback()
    return myBusyObject.execute(object : ActiveRunnable() {
      override fun run(): ActionCallback {
        focusManager.doWhenFocusSettlesDown(object : ExpirableRunnable.ForProject(myProject) {
          override fun run() {
            runnable.run()
            done.setDone()
          }
        })
        return done
      }
    })
  }

  override fun closeFile(file: VirtualFile) {
    val vals = myVirtualFile2Editor.remove(file)
    if (vals != null) {
      vals.first.forEachIndexed { i, fileEditor ->
        fileEditor.deselectNotify()
        vals.second[i].disposeEditor(fileEditor)
      }
    }
  }

  override fun openTextEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): Editor? {
    val fileEditors = openEditor(descriptor, focusEditor)
    for (fileEditor in fileEditors) {
      if (fileEditor is TextEditor) {
        setSelectedEditor(descriptor.getFile(), TextEditorProvider.getInstance().getEditorTypeId())
        return fileEditor.getEditor()
      }
    }
    return null
  }

  override fun getSelectedTextEditor(): Editor? {
    return selectedEditor
  }

  override fun isFileOpen(file: VirtualFile): Boolean {
    return myVirtualFile2Editor.containsKey(file)
  }

  override fun getOpenFiles(): Array<out VirtualFile> {
    return VfsUtilCore.toVirtualFileArray(myVirtualFile2Editor.keySet())
  }

  override fun getSelectedFiles(): Array<out VirtualFile> {
    throw UnsupportedOperationException()
  }

  override fun getSelectedEditors(): Array<out FileEditor> {
    return myVirtualFile2Editor.values().flatMap { it.first.toArrayList() }.toTypedArray()
  }

  override fun getSelectedEditor(file: VirtualFile): FileEditor? {
    val editors = myVirtualFile2Editor[file]?.first
    return editors?.get(0)
  }

  override fun getEditors(file: VirtualFile): Array<out FileEditor> {
    return myVirtualFile2Editor[file]?.first ?: EMPTY_EDITOR_ARRAY
  }

  override fun getAllEditors(file: VirtualFile): Array<out FileEditor> {
    return getEditors(file)
  }

  override fun getAllEditors(): Array<out FileEditor> {
    return myVirtualFile2Editor.values().flatMap { it.first.toArrayList() }.toTypedArray()
  }

  override fun showEditorAnnotation(editor: FileEditor, annotationComponent: JComponent) {
    throw UnsupportedOperationException()
  }

  override fun removeEditorAnnotation(editor: FileEditor, annotationComponent: JComponent) {
    throw UnsupportedOperationException()
  }

  override fun addTopComponent(editor: FileEditor, component: JComponent) {
    throw UnsupportedOperationException()
  }

  override fun removeTopComponent(editor: FileEditor, component: JComponent) {
    throw UnsupportedOperationException()
  }

  override fun addBottomComponent(editor: FileEditor, component: JComponent) {
    throw UnsupportedOperationException()
  }

  override fun removeBottomComponent(editor: FileEditor, component: JComponent) {
    throw UnsupportedOperationException()
  }

  override fun addFileEditorManagerListener(listener: FileEditorManagerListener) {
    myListenerList.add(listener)
  }

  override fun addFileEditorManagerListener(listener: FileEditorManagerListener, parentDisposable: Disposable) {
    myListenerList.add(listener, parentDisposable)
  }

  override fun removeFileEditorManagerListener(listener: FileEditorManagerListener) {
    myListenerList.remove(listener)
  }

  private fun navigateAndSelectEditor(editor: NavigatableFileEditor, descriptor: OpenFileDescriptor): Boolean {
    if (editor.canNavigateTo(descriptor)) {
      editor.navigateTo(descriptor)
      return true
    }

    return false
  }

  override fun openEditor(descriptor: OpenFileDescriptor, focusEditor: Boolean): List<FileEditor> {
    if (descriptor.getFile() is VirtualFileWindow) {
      val delegate = descriptor.getFile() as VirtualFileWindow
      val hostOffset = delegate.getDocumentWindow().injectedToHost(descriptor.getOffset())
      val realDescriptor = OpenFileDescriptor(descriptor.getProject(), delegate.getDelegate(), hostOffset)
      realDescriptor.setUseCurrentWindow(descriptor.isUseCurrentWindow())
      return openEditor(realDescriptor, focusEditor)
    }

    val result = SmartList<FileEditor>()
    CommandProcessor.getInstance().executeCommand(myProject, object : Runnable {
      override fun run() {
        val file = descriptor.getFile()
        val editors = openFile(file, focusEditor, !descriptor.isUseCurrentWindow())
        ContainerUtil.addAll<FileEditor, FileEditor, List<FileEditor>>(result, *editors)

        var navigated = false
        for (editor in editors) {
          if (editor is NavigatableFileEditor && getSelectedEditor(descriptor.getFile()) === editor) {
            // try to navigate opened editor
            navigated = navigateAndSelectEditor(editor, descriptor)
            if (navigated) break
          }
        }

        if (!navigated) {
          for (editor in editors) {
            if (editor is NavigatableFileEditor && getSelectedEditor(descriptor.getFile()) !== editor) {
              // try other editors
              if (navigateAndSelectEditor(editor, descriptor)) {
                break
              }
            }
          }
        }
      }
    }, "", null)

    return result
  }

  override fun getProject(): Project {
    return myProject
  }

  override fun setSelectedEditor(file: VirtualFile, fileEditorProviderId: String) {
    setActive(file)
  }

  override fun getReady(requestor: Any): ActionCallback {
    throw UnsupportedOperationException()
  }

  fun openFileImpl(file: VirtualFile, focusEditor: Boolean): Pair<Array<FileEditor>, Array<FileEditorProvider>> {
    assert(ApplicationManager.getApplication().isDispatchThread() || !ApplicationManager.getApplication().isReadAccessAllowed(), "must not open files under read action since we are doing a lot of invokeAndWaits here")

    if (myVirtualFile2Editor.containsKey(file)) {
      if (focusEditor) {
        setActive(file)
      }
      return myVirtualFile2Editor[file]
    }


    file.refresh(true, false)

    val newProviders: Array<FileEditorProvider>?
    var builders: Array<AsyncFileEditorProvider.Builder?>
    // File is not opened yet. In this case we have to create editors
    // and select the created EditorComposite.
    newProviders = FileEditorProviderManager.getInstance().getProviders(myProject, file)
    if (newProviders!!.size() == 0) {
      return Pair.create(EMPTY_EDITOR_ARRAY, EMPTY_PROVIDER_ARRAY)
    }

    builders = arrayOfNulls<AsyncFileEditorProvider.Builder>(newProviders.size())
    for (i in newProviders.indices) {
      try {
        val provider = newProviders[i]
        builders[i] = ApplicationManager.getApplication().runReadAction(object : Computable<AsyncFileEditorProvider.Builder> {
          override fun compute(): AsyncFileEditorProvider.Builder? {
            if (myProject.isDisposed() || !file.isValid()) {
              return null
            }
            LOG.assertTrue(provider.accept(myProject, file), "Provider " + provider + " doesn't accept file " + file)
            return if (provider is AsyncFileEditorProvider) provider.createEditorAsync(myProject, file) else null
          }
        })
      } catch (e: ProcessCanceledException) {
        throw e
      } catch (e: Exception) {
        LOG.error(e)
      } catch (e: AssertionError) {
        LOG.error(e)
      }

    }
    val newEditors: Array<FileEditor?> = arrayOfNulls(newProviders.size())
    UIUtil.invokeAndWaitIfNeeded(object : Runnable {
      override fun run() {
        if (myProject.isDisposed() || !file.isValid()) {
          return
        }

        getProject().getMessageBus()
            .syncPublisher(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER)
            .beforeFileOpened(this@ServerFileEditorManager, file)

        for (i in newProviders.indices) {
          try {
            val provider = newProviders[i]
            val editor = if (builders[i] == null) provider.createEditor(myProject, file) else builders[i]?.build()
            newEditors[i] = editor
          } catch (e: Exception) {
            LOG.error(e)
          } catch (e: AssertionError) {
            LOG.error(e)
          }
        }


        notifyPublisher(object : Runnable {
          override fun run() {
            getProject().getMessageBus()
                .syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER)
                .fileOpened(this@ServerFileEditorManager, file)
          }
        })
        val tabHost = Path("tab-view").getIn(model!!.root)!!.meta["host"] as TabViewHost
        val textEditor = newEditors[0] as TextEditor
        tabHost.addEditor(textEditor, file)
        setActive(file)

        //[jeka] this is a hack to support back-forward navigation
        // previously here was incorrect call to fireSelectionChanged() with a side-effect
        IdeDocumentHistory.getInstance(myProject).onSelectionChanged()
      }
    })
    return myVirtualFile2Editor[file]
  }

  private fun setActive(file: VirtualFile) {
    val newEditors = myVirtualFile2Editor[file].first
    newEditors.forEach {
      val editor = getAllEditors(file)
          .filter { it is TextEditor }
          .map { (it as TextEditor).getEditor() }
          .first()

      val host = editor.getUserData(EditorHost.editorHostKey)
      host.reactiveModel.transaction { m ->
        val tabs = host.path.dropLast(2).getIn(m)!!.meta["host"] as? TabViewHost
        val model = tabs?.setActiveEditor(m, host.path.components.last().toString()) ?: m

        IdeDocumentHistory.getInstance(myProject).onSelectionChanged()
        model
      }
    }
  }


}