// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ModalityStateListener
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrHandleException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.EditorEventMulticaster
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.event.EditorEventMulticasterImpl
import com.intellij.openapi.editor.impl.view.EditorPainter
import com.intellij.openapi.editor.impl.warmup.scheduleHighlighterWarmUp
import com.intellij.openapi.editor.impl.warmup.warmUpEditorClasses
import com.intellij.openapi.editor.impl.zombie.Necropolis
import com.intellij.openapi.editor.registry.EditorRegistryService
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileThreadingContracts
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.CharArrayCharSequence
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.stream.Stream
import kotlin.streams.asStream

private val EP: ExtensionPointName<EditorFactoryListener> = ExtensionPointName<EditorFactoryListener>("com.intellij.editorFactoryListener")
private val LOG: Logger = logger<EditorFactoryImpl>()

class EditorFactoryImpl(private val coroutineScope: CoroutineScope) : EditorFactory() {
  private val editorEventMulticaster: EditorEventMulticasterImpl = EditorEventMulticasterImpl()
  private val editorFactoryEventDispatcher: EventDispatcher<EditorFactoryListener?> = EventDispatcher.create(EditorFactoryListener::class.java)
  private val classPreparation = coroutineScope.async(
    context = Dispatchers.Default + CoroutineName("editor classes preparation"),
    start = CoroutineStart.LAZY,
  ) {
    warmUpEditorClasses(EP)
  }

  init {
    EditorRegistryService.ensureCreated()
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        // validate all editors are disposed after fireProjectClosed() was called, because it's the place where editor should be released
        Disposer.register(project) {
          // EditorTextField.releaseEditorLater defer releasing its editor; invokeLater to avoid false positives about such editors.
          coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            runCatching {
              validateEditorsAreReleased(project = project, isLastProjectClosed = serviceAsync<ProjectManager>().openProjects.isEmpty())
            }.getOrHandleException {
              LOG.warn("An editor is not released after its project is closed", it)
            }
          }
        }
      }
    })
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshAllEditorsAfterColorsSchemeChange() })
    connection.subscribe(AdvancedSettingsChangeListener.TOPIC, object : AdvancedSettingsChangeListener {
      override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
        if (id == EditorGutterComponentImpl.DISTRACTION_FREE_MARGIN || id == EditorPainter.EDITOR_TAB_PAINTING || id == EDITOR_SHOW_SPECIAL_CHARS) {
          refreshAllEditors()
        }
      }
    })

    LaterInvocator.addModalityStateListener(object : ModalityStateListener {
      override fun beforeModalityStateChanged(entering: Boolean, modalEntity: Any) {
        collectAllEditors().forEach { (it as EditorImpl).beforeModalityStateChanged() }
      }
    }, ApplicationManager.getApplication())
  }

  @ApiStatus.Internal
  companion object {
    fun throwNotReleasedError(editor: Editor) {
      if (editor is EditorImpl) {
        editor.throwDisposalError("Editor $editor hasn't been released:")
      }
      throw RuntimeException("""Editor of ${editor.javaClass} and the following text hasn't been released: ${editor.document.getImmutableCharSequence()}""")
    }
  }

  @VisibleForTesting
  fun validateEditorsAreReleased(project: Project, isLastProjectClosed: Boolean) {
    for (editor in collectAllEditors()) {
      if (editor.project === project || (editor.project == null && isLastProjectClosed)) {
        try {
          throwNotReleasedError(editor)
        }
        finally {
          releaseEditor(editor)
        }
      }
    }
  }

  /**
   * Starts the BG preparation of the editor classes and returns at once.
   * Call it where an editor comes soon, but the code cannot wait for [prepareEditorClasses].
   */
  @ApiStatus.Internal
  fun startEditorClassesPreparation() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment) {
      return
    }
    classPreparation.start()
  }

  @ApiStatus.Internal
  fun startHighlighterPreparation(project: Project?, document: Document?, fileType: FileType?) {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || project == null || document == null && fileType == null) {
      return
    }

    coroutineScope.scheduleHighlighterWarmUp(project, document, fileType)
  }

  /**
   * Starts the BG preparation of the editor classes if necessary and waits for its end.
   * Call it before the EDT creates an editor. Unlike [startEditorClassesPreparation], it also runs in a headless environment.
   */
  @ApiStatus.Internal
  suspend fun prepareEditorClasses() {
    val preparation = classPreparation
    preparation.await()
  }

  override fun createDocument(text: CharArray): Document = createDocument(CharArrayCharSequence(*text))

  override fun createDocument(text: CharSequence): Document {
    val document = DocumentImpl(text)
    editorEventMulticaster.registerDocument(document)
    return document
  }

  override fun createDocument(allowUpdatesWithoutWriteAction: Boolean): Document {
    val document = DocumentImpl("", allowUpdatesWithoutWriteAction)
    editorEventMulticaster.registerDocument(document)
    return document
  }

  override fun createDocument(text: CharSequence, acceptsSlashR: Boolean, allowUpdatesWithoutWriteAction: Boolean): Document {
    val document = DocumentImpl(text, acceptsSlashR, allowUpdatesWithoutWriteAction)
    editorEventMulticaster.registerDocument(document)
    return document
  }

  override fun refreshAllEditors() {
    for (editor in ClientEditorManager.getCurrentInstance().editors) {
      if (isEditorLoaded(editor)) {
        (editor as EditorEx).reinitSettings()
      }
    }
  }

  /**
   * Refreshes all editors as [refreshAllEditors] does. The new colors scheme can give the text other font styles,
   * so an [EditorImpl] also recalculates its soft wraps.
   */
  private fun refreshAllEditorsAfterColorsSchemeChange() {
    for (editor in ClientEditorManager.getCurrentInstance().editors) {
      if (isEditorLoaded(editor)) {
        if (editor is EditorImpl) {
          editor.reinitSettingsAfterColorsSchemeChange()
        } else {
          (editor as EditorEx).reinitSettings()
        }
      }
    }
  }

  override fun createEditor(document: Document): Editor {
    return createEditor(document = document, isViewer = false, project = null, kind = EditorKind.UNTYPED)
  }

  override fun createViewer(document: Document): Editor {
    return createEditor(document = document, isViewer = true, project = null, kind = EditorKind.UNTYPED)
  }

  override fun createEditor(document: Document, project: Project?): Editor {
    return createEditor(document = document, isViewer = false, project = project, kind = EditorKind.UNTYPED)
  }

  override fun createEditor(document: Document, project: Project?, kind: EditorKind): Editor {
    return createEditor(document = document, isViewer = false, project = project, kind = kind)
  }

  override fun createViewer(document: Document, project: Project?): Editor {
    return createEditor(document = document, isViewer = true, project = project, kind = EditorKind.UNTYPED)
  }

  override fun createViewer(document: Document, project: Project?, kind: EditorKind): Editor {
    return createEditor(document = document, isViewer = true, project = project, kind = kind)
  }

  override fun createEditor(document: Document, project: Project?, fileType: FileType, isViewer: Boolean): Editor {
    val editor = createEditor(document = document, isViewer = isViewer, project = project, kind = EditorKind.UNTYPED)
    editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, fileType)
    return editor
  }

  override fun createEditor(document: Document, project: Project?, file: VirtualFile, isViewer: Boolean): Editor {
    val editor = createEditor(document = document, isViewer = isViewer, project = project, kind = EditorKind.UNTYPED)
    editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
    return editor
  }

  override fun createEditor(document: Document, project: Project?, file: VirtualFile, isViewer: Boolean, kind: EditorKind): Editor {
    val editor = createEditor(document = document, isViewer = isViewer, project = project, kind = kind)
    editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, file)
    return editor
  }

  private fun createEditor(document: Document, isViewer: Boolean, project: Project?, kind: EditorKind): EditorImpl {
    val hostDocument = PsiDocumentManagerBase.getTopLevelDocument(document)
    return doCreateEditor(project = project, document = hostDocument, isViewer = isViewer, kind = kind, file = null, highlighter = null, afterCreation = null)
  }

  @ApiStatus.Internal
  fun createMainEditor(
    document: Document,
    project: Project,
    file: VirtualFile,
    highlighter: EditorHighlighter?,
    afterCreation: ((EditorImpl) -> Unit)?,
  ): EditorImpl {
    assert(document !is DocumentWindow)
    return doCreateEditor(
      project = project,
      document = document,
      isViewer = false,
      kind = EditorKind.MAIN_EDITOR,
      file = file,
      highlighter = highlighter,
      afterCreation = afterCreation,
    )
  }

  private fun doCreateEditor(
    project: Project?,
    document: Document,
    isViewer: Boolean,
    kind: EditorKind,
    file: VirtualFile?,
    highlighter: EditorHighlighter?,
    afterCreation: ((EditorImpl) -> Unit)?,
  ): EditorImpl {
    val editor = EditorImpl(document, isViewer, project, kind, file, highlighter)
    registerEditor(editor, kind, file, afterCreation)
    return editor
  }

  @ApiStatus.Internal
  @RequiresEdt
  fun prepareMainEditor(document: Document, project: Project, file: VirtualFile): PendingMainEditor {
    assert(document !is DocumentWindow)
    return PendingMainEditor(EditorImpl(document, false, project, EditorKind.MAIN_EDITOR, file, null), file)
  }

  /**
   * Holds an editor during highlighter preparation. Close it if construction does not transfer the editor to a file editor.
   */
  @ApiStatus.Internal
  inner class PendingMainEditor internal constructor(private val editor: EditorImpl, private val file: VirtualFile) : AutoCloseable {
    private var registered = false
    private var closed = false

    @RequiresEdt
    fun complete(highlighter: EditorHighlighter, afterCreation: (EditorImpl) -> Unit): EditorImpl {
      check(!registered && !closed)
      editor.highlighter = highlighter
      registerEditor(editor, EditorKind.MAIN_EDITOR, file, afterCreation) { registered = true }
      return editor
    }

    @RequiresEdt
    override fun close() {
      if (closed) return
      closed = true
      if (registered) releaseEditor(editor) else editor.release()
    }
  }

  private fun registerEditor(
    editor: EditorImpl,
    kind: EditorKind,
    file: VirtualFile?,
    afterCreation: ((EditorImpl) -> Unit)?,
    onRegistered: (() -> Unit)? = null,
  ) {
    editor.putEditorId()
    markLightFileExposedInEditor(kind, file)
    // must be _before_ event firing
    afterCreation?.invoke(editor)

    val editorManager = ClientEditorManager.getCurrentInstance()
    editorManager.editorCreated(editor)
    onRegistered?.invoke()
    editorEventMulticaster.registerEditor(editor)

    val event = EditorFactoryEvent(this, editor)
    editorFactoryEventDispatcher.multicaster.editorCreated(event)
    EP.forEachExtensionSafe { it.editorCreated(event) }

    LOG.debug { "number of editors after create: ${editorManager.editorsSequence().count()}" }
  }

  @RequiresEdt
  override fun releaseEditor(editor: Editor) {
    try {
      turnIntoZombiesAndBury(editor)
      val event = EditorFactoryEvent(this, editor)
      editorFactoryEventDispatcher.multicaster.editorReleased(event)
      EP.forEachExtensionSafe { it.editorReleased(event) }
    }
    finally {
      try {
        if (editor is EditorImpl) {
          editor.release()
        }
      }
      finally {
        for (clientEditors in ClientEditorManager.getAllInstances()) {
          if (clientEditors.editorReleased(editor)) {
            LOG.debug { "number of Editors after release: ${clientEditors.editorsSequence().count()}" }
            //don't try creating the service to avoid CancellationException
            val currentInstance = ClientEditorManager.getCurrentInstanceIfCreated()
            if (currentInstance != null && clientEditors != currentInstance) {
              LOG.warn("Released editor didn't belong to current session")
            }
            break
          }
        }
      }
    }
  }

  override fun editors(document: Document, project: Project?): Stream<Editor> {
    return collectAllEditors()
      .filter { editor -> editor.document == document && (project == null || project == editor.project) }
      .asStream()
  }

  override fun getEditorList(): List<Editor> = collectAllEditors().toList()

  override fun getAllEditors(): Array<Editor> {
    val list = getEditorList()
    return if (list.isEmpty()) Editor.EMPTY_ARRAY else list.toTypedArray()
  }

  @Suppress("removal", "OVERRIDE_DEPRECATION")
  override fun addEditorFactoryListener(listener: EditorFactoryListener) {
    editorFactoryEventDispatcher.addListener(listener)
  }

  override fun addEditorFactoryListener(listener: EditorFactoryListener, parentDisposable: Disposable) {
    editorFactoryEventDispatcher.addListener(listener, parentDisposable)
  }

  @Suppress("removal", "OVERRIDE_DEPRECATION")
  override fun removeEditorFactoryListener(listener: EditorFactoryListener) {
    editorFactoryEventDispatcher.removeListener(listener)
  }

  override fun getEventMulticaster(): EditorEventMulticaster = editorEventMulticaster

  /**
   * Must be called before the listeners because they could do disposing things corrupting the editor's state,
   * see CodeVisionHost
   */
  private fun turnIntoZombiesAndBury(editor: Editor) {
    val necropolis = editor.project?.let {
      Necropolis.getInstance(it, onlyIfCreated = true)
    }
    necropolis?.turnIntoZombiesAndBury(editor)
  }
}

private fun collectAllEditors(): Sequence<Editor> {
  return ClientEditorManager.getAllInstances().asSequence().flatMap { it.editorsSequence() }
}

private fun markLightFileExposedInEditor(kind: EditorKind, file: VirtualFile?) {
  if (kind == EditorKind.MAIN_EDITOR && file is LightVirtualFile) {
    FileThreadingContracts.markLightFileRequiresApplicationLockOnModification(file)
  }
}
