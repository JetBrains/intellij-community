// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl.text

import com.intellij.ide.IdeBundle
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.lang.Language
import com.intellij.openapi.application.ApplicationManager
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
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.annotations.NonNls
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import javax.swing.JComponent

private val TRANSIENT_EDITOR_STATE_KEY = Key.create<TransientEditorState>("transientState")

open class TextEditorImpl @JvmOverloads constructor(@JvmField val project: Project,
                                                    @JvmField protected val myFile: VirtualFile,
                                                    provider: TextEditorProvider,
                                                    editor: EditorImpl = createEditor(project, myFile)) : UserDataHolderBase(), TextEditor {
  private val changeSupport = PropertyChangeSupport(this)
  private val component: TextEditorComponent
  private val myAsyncLoader: AsyncEditorLoader

  internal val file: VirtualFile
    get() = myFile

  init {
    component = createEditorComponent(project, myFile, editor)
    for (customizer in TextEditorCustomizer.EP.extensionList) {
      @Suppress("LeakingThis")
      customizer.customize(this)
    }
    val state = myFile.getUserData(TRANSIENT_EDITOR_STATE_KEY)
    if (state != null) {
      state.applyTo(activeEditor)
      myFile.putUserData(TRANSIENT_EDITOR_STATE_KEY, null)
    }
    Disposer.register(this, component)
    myAsyncLoader = project.getService(AsyncEditorLoaderService::class.java).start(this, component, provider)
  }

  companion object {
    fun getDocumentLanguage(editor: Editor): Language? {
      val project = editor.project!!
      if (!project.isDisposed) {
        val documentManager = PsiDocumentManager.getInstance(project)
        val file = documentManager.getPsiFile(editor.document)
        if (file != null) {
          return file.language
        }
      }
      else {
        logger<TextEditorImpl>().warn("Attempting to get a language for document on a disposed project: " + project.name)
      }
      return null
    }
  }

  /**
   * @return a continuation to be called in EDT
   */
  open fun loadEditorInBackground(): Runnable {
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    val scheme = EditorColorsManager.getInstance().globalScheme
    val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(myFile, scheme, project)
    val editor = activeEditor
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
    myAsyncLoader.dispose()
    if (myFile.getUserData(FileEditorManagerImpl.CLOSING_TO_REOPEN) == true) {
      myFile.putUserData(TRANSIENT_EDITOR_STATE_KEY, TransientEditorState.forEditor(editor))
    }
  }

  override fun getFile(): VirtualFile {
    return myFile
  }

  override fun getComponent(): JComponent {
    return component
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return activeEditor.contentComponent
  }

  override fun getEditor(): EditorEx {
    return activeEditor
  }

  /**
   * @see TextEditorComponent.editor
   */
  private val activeEditor: EditorEx
    get() = component.editor

  override fun getName(): String {
    return IdeBundle.message("tab.title.text")
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    return myAsyncLoader.getEditorState(level)
  }

  override fun setState(state: FileEditorState) {
    setState(state, false)
  }

  override fun setState(state: FileEditorState, exactState: Boolean) {
    if (state is TextEditorState) {
      myAsyncLoader.setEditorState(state, exactState)
    }
  }

  override fun isModified(): Boolean {
    return component.isModified
  }

  override fun isValid(): Boolean {
    return component.isEditorValid
  }

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
    val document: Document = component.editor.document
    val file = FileDocumentManager.getInstance().getFile(document)
    return if (file == null || !file.isValid) {
      null
    }
    else StructureViewBuilder.PROVIDER.getStructureViewBuilder(file.fileType, file, project)
  }

  override fun canNavigateTo(navigatable: Navigatable): Boolean {
    return navigatable is OpenFileDescriptor &&
           (navigatable.line >= 0 || navigatable.offset >= 0)
  }

  override fun navigateTo(navigatable: Navigatable) {
    (navigatable as OpenFileDescriptor).navigateIn(editor)
  }

  override fun toString(): @NonNls String {
    return "Editor: " + component.file
  }

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

private fun createEditor(project: Project, file: VirtualFile): EditorImpl {
  val document = FileDocumentManager.getInstance().getDocument(file, project)
  val factory = EditorFactory.getInstance() as EditorFactoryImpl
  return factory.createMainEditor(document!!, project, file)
}