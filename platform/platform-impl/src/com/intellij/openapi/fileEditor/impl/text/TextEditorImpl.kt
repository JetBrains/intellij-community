// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.diagnostic.PluginException
import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
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
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorImpl.Companion.getDocumentLanguage
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.serviceContainer.ComponentManagerImpl
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

private val TRANSIENT_EDITOR_STATE_KEY = Key.create<TransientEditorState>("transientState")
private val TEXT_EDITOR_CUSTOMIZER_EP: ExtensionPointName<TextEditorCustomizer> = ExtensionPointName("com.intellij.textEditorCustomizer")

open class TextEditorImpl @Internal constructor(
  @JvmField protected val project: Project,
  @JvmField protected val file: VirtualFile,
  private val component: TextEditorComponent,
  @JvmField @Internal val asyncLoader: AsyncEditorLoader,
  startLoading: Boolean,
) : UserDataHolderBase(), TextEditor {
  @Suppress("LeakingThis")
  private val changeSupport = PropertyChangeSupport(this)

  // for backward-compatibility only
  constructor(
    project: Project,
    file: VirtualFile,
    componentAndLoader: Pair<TextEditorComponent, AsyncEditorLoader>,
  ) : this(
    project = project,
    file = file,
    component = componentAndLoader.first,
    asyncLoader = componentAndLoader.second,
    startLoading = true,
  )

  init {
    @Suppress("LeakingThis")
    TextEditorProvider.putTextEditor(component.editor, this)

    if (startLoading) {
      @Suppress("LeakingThis")
      asyncLoader.start(
        textEditor = this,
        task = asyncLoader.coroutineScope.async(CoroutineName("HighlighterTextEditorInitializer")) {
          setHighlighterToEditor(project = project, file = file, document = editor.document, editor = component.editorImpl)
        },
      )
    }

    val state = file.getUserData(TRANSIENT_EDITOR_STATE_KEY)
    if (state != null) {
      file.putUserData(TRANSIENT_EDITOR_STATE_KEY, null)
      state.applyTo(component.editor)
    }

    // postpone subscribing - perform not in EDT
    asyncLoader.coroutineScope.launch {
      for (extension in TEXT_EDITOR_CUSTOMIZER_EP.filterableLazySequence()) {
        try {
          val customizer = extension.instance ?: continue
          val scope = asyncLoader.coroutineScope.childScope(extension.implementationClassName)
          scope.attachAsChildTo((project as ComponentManagerImpl).pluginCoroutineScope(customizer::class.java.classLoader))
          scope.launch {
            customizer.execute(textEditor = this@TextEditorImpl)
          }
        }
        catch (e: CancellationException) {
          throw e
        }
        catch (e: Throwable) {
          logger<TextEditorImpl>().error(PluginException(e, extension.pluginDescriptor.pluginId))
        }
      }

      component.listenChanges(parentDisposable = this@TextEditorImpl, asyncLoader = asyncLoader, textEditor = this@TextEditorImpl, project = project)
    }
  }

  companion object {
    @Internal
    fun getDocumentLanguage(editor: Editor): Language? {
      val project = editor.project ?: return LanguageUtil.getFileTypeLanguage(editor.virtualFile.fileType)
      if (project.isDisposed) {
        logger<TextEditorImpl>().warn("Attempting to get a language for document on a disposed project: ${project.name}")
        return null
      }
      else {
        return PsiDocumentManager.getInstance(project).getPsiFile(editor.document)?.language
      }
    }
  }

  override fun dispose() {
    try {
      asyncLoader.dispose()
    }
    finally {
      if (!component.isDisposed) {
        component.dispose()
      }
    }
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
      asyncLoader.setEditorState(state = state, exactState = exactState, editor = editor)
    }
  }

  override fun isModified(): Boolean = component.isModified

  override fun isValid(): Boolean = component.isEditorValid

  fun updateModifiedProperty() {
    component.updateModifiedProperty(this)
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
    return StructureViewBuilder.getProvider().getStructureViewBuilder(file.fileType, file, project)
  }

  final override fun canNavigateTo(navigatable: Navigatable): Boolean {
    return navigatable is OpenFileDescriptor && (navigatable.line >= 0 || navigatable.offset >= 0)
  }

  final override fun navigateTo(navigatable: Navigatable) {
    (navigatable as OpenFileDescriptor).navigateIn(editor)
  }

  override fun toString(): @NonNls String = "Editor: ${component.file}"

  final override fun isEditorLoaded(): Boolean = asyncLoader.isLoaded()
}

private class TransientEditorState {
  private var softWrapsEnabled = false

  companion object {
    fun forEditor(editor: Editor): TransientEditorState {
      val state = TransientEditorState()
      state.softWrapsEnabled = editor.settings.isUseSoftWraps
      return state
    }
  }

  fun applyTo(editor: Editor) {
    editor.settings.isUseSoftWraps = softWrapsEnabled
  }
}

private val tracer by lazy { TelemetryManager.getSimpleTracer(Scope("startup")) }

@Internal
fun createAsyncEditorLoader(
  provider: TextEditorProvider,
  project: Project,
  fileForTelemetry: VirtualFile,
  editorCoroutineScope: CoroutineScope?,
): AsyncEditorLoader {
  // `openEditorImpl` uses runWithModalProgressBlocking,
  // but an async editor load is performed in the background, out of the `openEditorImpl` call
  return AsyncEditorLoader(
    project = project,
    provider = provider,
    coroutineScope = (editorCoroutineScope ?: project.service<AsyncEditorLoaderScopeHolder>().coroutineScope).childScope(
      name = "AsyncEditorLoader(file=${fileForTelemetry.name})",
      supervisor = false,
      // name, not path (privacy)
      context = tracer.rootSpan("AsyncEditorLoader", arrayOf("file", fileForTelemetry.name)) + ModalityState.any().asContextElement(),
    ),
  )
}

private suspend fun setHighlighterToEditor(project: Project, file: VirtualFile, document: Document, editor: EditorImpl) {
  val scheme = serviceAsync<EditorColorsManager>().globalScheme
  val editorHighlighterFactory = serviceAsync<EditorHighlighterFactory>()
  val highlighter = readAction {
    val highlighter = editorHighlighterFactory.createEditorHighlighter(file, scheme, project)
    // editor.setHighlighter also sets text, but we set it here to avoid executing related work in EDT
    // (the document text is compared, so, double work is not performed)
    highlighter.setText(document.immutableCharSequence)
    highlighter
  }

  span("editor highlighter set", Dispatchers.EDT) {
    editor.settings.setLanguageSupplier {
      getDocumentLanguage(editor)
    }
    editor.highlighter = highlighter
  }
}

@Service(Service.Level.PROJECT)
private class AsyncEditorLoaderScopeHolder(@JvmField val coroutineScope: CoroutineScope)

@Internal
fun createEditorImpl(project: Project, file: VirtualFile, asyncLoader: AsyncEditorLoader): Pair<EditorImpl, AsyncEditorLoader> {
  val document = FileDocumentManager.getInstance().getDocument(file, project)!!
  return (EditorFactory.getInstance() as EditorFactoryImpl).createMainEditor(document = document, project = project, file = file, highlighter = null, afterCreation = {
    it.putUserData(AsyncEditorLoader.ASYNC_LOADER, asyncLoader)
  }) to asyncLoader
}

@Internal
fun TextEditorImpl.performWhenLoaded(action: () -> Unit) {
  AsyncEditorLoader.performWhenLoaded(asyncLoader, action)
}