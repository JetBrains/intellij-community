// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.editor.impl.EditorFactoryImpl
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.impl.rootTask
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.childScope
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent
import kotlin.coroutines.EmptyCoroutineContext

private val TRANSIENT_EDITOR_STATE_KEY = Key.create<TransientEditorState>("transientState")

open class TextEditorImpl @Internal constructor(@JvmField protected val project: Project,
                                                @JvmField protected val file: VirtualFile,
                                                editor: EditorImpl,
                                                private val asyncLoader: AsyncEditorLoader) : UserDataHolderBase(), TextEditor {
  @Suppress("LeakingThis")
  private val changeSupport = PropertyChangeSupport(this)
  private val component: TextEditorComponent

  constructor(project: Project,
              file: VirtualFile,
              provider: TextEditorProvider,
              editor: EditorImpl) : this(project = project,
                                         file = file,
                                         editor = editor,
                                         asyncLoader = createAsyncEditorLoader(provider, project)) {
    val editorSupplier = suspend { editor }
    @Suppress("LeakingThis")
    asyncLoader.start(textEditor = this, tasks = listOf(
      asyncLoader.coroutineScope.async(CoroutineName("HighlighterTextEditorInitializer")) {
        setHighlighterToEditor(project, file, editor.document, editorSupplier)
      },
    ))
  }

  init {
    @Suppress("LeakingThis")
    component = createEditorComponent(project = project, file = file, editor = editor)
    for (customizer in TextEditorCustomizer.EP.extensionList) {
      @Suppress("LeakingThis")
      customizer.customize(this)
    }
    val state = file.getUserData(TRANSIENT_EDITOR_STATE_KEY)
    if (state != null) {
      state.applyTo(component.editor)
      file.putUserData(TRANSIENT_EDITOR_STATE_KEY, null)
    }

    @Suppress("LeakingThis")
    Disposer.register(this, component)
  }

  // don't pollute global scope
  companion object {
    fun createAsyncEditorLoader(provider: TextEditorProvider, project: Project): AsyncEditorLoader {
      // `openEditorImpl` uses runWithModalProgressBlocking,
      // but an async editor load is performed in the background, out of the `openEditorImpl` call
      val modality = ModalityState.any().asContextElement()

      val context = if (StartUpMeasurer.isEnabled()) rootTask() else EmptyCoroutineContext
      return AsyncEditorLoader(
        project = project,
        provider = provider,
        coroutineScope = project.service<AsyncEditorLoaderService>().coroutineScope.childScope(supervisor = false,
                                                                                               context = context + modality),
      )
    }

    @Internal
    fun getDocumentLanguage(editor: Editor): Language? {
      val project = editor.project!!
      if (project.isDisposed) {
        logger<TextEditorImpl>().warn("Attempting to get a language for document on a disposed project: ${project.name}")
        return null
      }
      else {
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.language
      }
    }

    @Internal
    fun createTextEditor(project: Project, file: VirtualFile): EditorImpl {
      val document = FileDocumentManager.getInstance().getDocument(file, project)
      val factory = EditorFactory.getInstance() as EditorFactoryImpl
      return factory.createMainEditor(document!!, project, file)
    }

    suspend fun setHighlighterToEditor(project: Project,
                                       file: VirtualFile,
                                       document: Document,
                                       editorSupplier: suspend () -> EditorEx) {
      val scheme = serviceAsync<EditorColorsManager>().globalScheme
      val editorHighlighterFactory = serviceAsync<EditorHighlighterFactory>()
      val highlighter = readAction {
        val highlighter = editorHighlighterFactory.createEditorHighlighter(file, scheme, project)
        // editor.setHighlighter also sets text, but we set it here to avoid executing related work in EDT
        // (the document text is compared, so, double work is not performed)
        highlighter.setText(document.immutableCharSequence)
        highlighter
      }

      val editor = editorSupplier()
      span("editor highlighter set", Dispatchers.EDT) {
        editor.settings.setLanguageSupplier { getDocumentLanguage(editor) }
        editor.highlighter = highlighter
      }
    }

  }

  protected open fun createEditorComponent(project: Project, file: VirtualFile, editor: EditorImpl): TextEditorComponent {
    return TextEditorComponent(project, file, this, editor)
  }

  override fun dispose() {
    asyncLoader.dispose()
    if (file.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) == true) {
      file.putUserData(TRANSIENT_EDITOR_STATE_KEY, TransientEditorState.forEditor(editor))
    }
  }

  override fun getFile(): VirtualFile = file

  override fun getComponent(): TextEditorComponent = component

  override fun getPreferredFocusedComponent(): JComponent = component.editor.contentComponent

  override fun getEditor(): EditorEx = component.editor

  override fun getName(): String = IdeBundle.message("tab.title.text")

  override fun getState(level: FileEditorStateLevel): FileEditorState = asyncLoader.getEditorState(level, editor)

  override fun setState(state: FileEditorState) {
    setState(state = state, exactState = false)
  }

  override fun setState(state: FileEditorState, exactState: Boolean) {
    if (state is TextEditorState) {
      asyncLoader.setEditorState(state = state, exactState = exactState, editor)
    }
  }

  override fun isModified(): Boolean = component.isModified

  override fun isValid(): Boolean = component.isEditorValid

  fun updateModifiedProperty() {
    component.updateModifiedProperty()
  }

  fun firePropertyChange(propertyName: String, oldValue: Any?, newValue: Any?) {
    changeSupport.firePropertyChange(propertyName, oldValue, newValue)
  }

  override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    changeSupport.addPropertyChangeListener(listener)
  }

  override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    changeSupport.removePropertyChangeListener(listener)
  }

  override fun getCurrentLocation(): FileEditorLocation? {
    return TextEditorLocation(editor.caretModel.logicalPosition, this)
  }

  override fun getStructureViewBuilder(): StructureViewBuilder? {
    val file = FileDocumentManager.getInstance().getFile(component.editor.document)?.takeIf { it.isValid } ?: return null
    return StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.fileType, file, project)
  }

  override fun canNavigateTo(navigatable: Navigatable): Boolean {
    return navigatable is OpenFileDescriptor && (navigatable.line >= 0 || navigatable.offset >= 0)
  }

  override fun navigateTo(navigatable: Navigatable) {
    (navigatable as OpenFileDescriptor).navigateIn(editor)
  }

  override fun toString(): @NonNls String = "Editor: ${component.file}"
}

private class TransientEditorState {
  private var softWrapsEnabled = false

  fun applyTo(editor: Editor) {
    editor.settings.isUseSoftWraps = softWrapsEnabled
  }

  companion object {
    fun forEditor(editor: Editor): TransientEditorState {
      val state = TransientEditorState()
      state.softWrapsEnabled = editor.settings.isUseSoftWraps
      return state
    }
  }
}

@Service(Service.Level.PROJECT)
private class AsyncEditorLoaderService(@JvmField val coroutineScope: CoroutineScope)