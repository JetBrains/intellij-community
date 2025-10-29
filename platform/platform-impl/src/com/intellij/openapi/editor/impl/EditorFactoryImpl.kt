// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.injected.editor.DocumentWindow
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.ActionPlan
import com.intellij.openapi.editor.actionSystem.TypedActionHandler
import com.intellij.openapi.editor.actionSystem.TypedActionHandlerEx
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
import com.intellij.openapi.editor.impl.zombie.Necropolis
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.impl.text.AsyncEditorLoader.Companion.isEditorLoaded
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.removeUserData
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.text.CharArrayCharSequence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.stream.Stream
import kotlin.streams.asStream

private val EP = ExtensionPointName<EditorFactoryListener>("com.intellij.editorFactoryListener")
private val LOG = logger<EditorFactoryImpl>()

class EditorFactoryImpl(coroutineScope: CoroutineScope?) : EditorFactory() {
  private val editorEventMulticaster = EditorEventMulticasterImpl()
  private val editorFactoryEventDispatcher = EventDispatcher.create(EditorFactoryListener::class.java)

  init {
    val connection = ApplicationManager.getApplication().messageBus.simpleConnect()
    connection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosed(project: Project) {
        // validate all editors are disposed after fireProjectClosed() was called, because it's the place where editor should be released
        Disposer.register(project) {
          // EditorTextField.releaseEditorLater defer releasing its editor; invokeLater to avoid false positives about such editors.
          coroutineScope?.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            LOG.runCatching {
              validateEditorsAreReleased(project = project, isLastProjectClosed = serviceAsync<ProjectManager>().openProjects.isEmpty())
            }
          }
        }
      }
    })
    connection.subscribe(EditorColorsManager.TOPIC, EditorColorsListener { refreshAllEditors() })
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

  override fun createDocument(text: CharArray): Document = createDocument(CharArrayCharSequence(*text))

  override fun createDocument(text: CharSequence): Document {
    val document = DocumentImpl(text)
    editorEventMulticaster.registerDocument(document)
    return document
  }

  fun createDocument(allowUpdatesWithoutWriteAction: Boolean): Document {
    val document = DocumentImpl("", allowUpdatesWithoutWriteAction)
    editorEventMulticaster.registerDocument(document)
    return document
  }

  fun createDocument(text: CharSequence, acceptsSlashR: Boolean, allowUpdatesWithoutWriteAction: Boolean): Document {
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
    hackyPutEditorIdToDocument(document)
    val editor = EditorImpl(document, isViewer, project, kind, file, highlighter)
    putEditorId(document, editor)
    // must be _before_ event firing
    afterCreation?.invoke(editor)

    val editorManager = ClientEditorManager.getCurrentInstance()
    editorManager.editorCreated(editor)
    editorEventMulticaster.registerEditor(editor)

    val event = EditorFactoryEvent(this, editor)
    editorFactoryEventDispatcher.multicaster.editorCreated(event)
    EP.forEachExtensionSafe { it.editorCreated(event) }

    LOG.debug { "number of editors after create: ${editorManager.editorsSequence().count()}" }
    return editor
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
            if (clientEditors != ClientEditorManager.getCurrentInstance()) {
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

@Suppress("unused")
private class MyRawTypedHandler(private val delegate: TypedActionHandler) : TypedActionHandlerEx {
  override fun execute(editor: Editor, charTyped: Char, dataContext: DataContext) {
    editor.putUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION, true)
    try {
      delegate.execute(editor, charTyped, dataContext)
    }
    finally {
      editor.putUserData(EditorImpl.DISABLE_CARET_SHIFT_ON_WHITESPACE_INSERTION, null)
    }
  }

  override fun beforeExecute(editor: Editor, c: Char, context: DataContext, plan: ActionPlan) {
    if (delegate is TypedActionHandlerEx) {
      delegate.beforeExecute(editor, c, context, plan)
    }
  }
}

private fun collectAllEditors(): Sequence<Editor> {
  return ClientEditorManager.getAllInstances().asSequence().flatMap { it.editorsSequence() }
}

private fun hackyPutEditorIdToDocument(document: Document) {
  if (isRhizomeAdEnabled) {
    if (document.getUserData(KERNEL_EDITOR_ID_KEY) == null) {
      document.putUserData(KERNEL_EDITOR_ID_KEY, EditorId.create())
    }
  }
}

private fun putEditorId(document: Document, editor: EditorImpl) {
  if (isRhizomeAdEnabled) {
    editor.putUserData(KERNEL_EDITOR_ID_KEY, document.removeUserData(KERNEL_EDITOR_ID_KEY))
  }
  else {
    editor.putEditorId()
  }
}
