// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
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
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

private val TRANSIENT_EDITOR_STATE_KEY = Key.create<TransientEditorState>("transientState")

open class TextEditorImpl(@JvmField protected val project: Project,
                          @JvmField protected val file: VirtualFile,
                          provider: TextEditorProvider,
                          editor: EditorImpl) : UserDataHolderBase(), TextEditor {
  @Suppress("LeakingThis")
  private val changeSupport = PropertyChangeSupport(this)
  private val component: TextEditorComponent
  private val asyncLoader: AsyncEditorLoader

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

    val service = project.service<AsyncEditorLoaderService>()
    @Suppress("LeakingThis")
    asyncLoader = AsyncEditorLoader(project = project,
                                    textEditor = this,
                                    editorComponent = component,
                                    provider = provider,
                                    coroutineScope = service.coroutineScope.childScope(supervisor = false))
    asyncLoader.start()
  }

  // don't pollute global scope
  companion object {
    @ApiStatus.Internal
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

    @ApiStatus.Internal
    fun createTextEditor(project: Project, file: VirtualFile): EditorImpl {
      val document = FileDocumentManager.getInstance().getDocument(file, project)
      val factory = EditorFactory.getInstance() as EditorFactoryImpl
      return factory.createMainEditor(document!!, project, file)
    }
  }

  /**
   * @return a continuation to be called in EDT
   */
  @RequiresBackgroundThread
  open fun loadEditorInBackground(): Runnable {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val scheme = EditorColorsManager.getInstance().globalScheme
    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(file, scheme, project)
    val editor = component.editor
    highlighter.setText(editor.document.immutableCharSequence)
    return Runnable {
      editor.settings.setLanguageSupplier { getDocumentLanguage(editor) }
      editor.highlighter = highlighter
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

  override fun getComponent(): JComponent = component

  override fun getPreferredFocusedComponent(): JComponent = component.editor.contentComponent

  override fun getEditor(): EditorEx = component.editor

  override fun getName(): String = IdeBundle.message("tab.title.text")

  override fun getState(level: FileEditorStateLevel): FileEditorState = asyncLoader.getEditorState(level)

  override fun setState(state: FileEditorState) {
    setState(state = state, exactState = false)
  }

  override fun setState(state: FileEditorState, exactState: Boolean) {
    if (state is TextEditorState) {
      asyncLoader.setEditorState(state = state, exactState = exactState)
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