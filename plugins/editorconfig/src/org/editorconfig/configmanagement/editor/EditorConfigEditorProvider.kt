// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement.editor

import com.intellij.application.options.CodeStyle
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorProvider
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import org.editorconfig.configmanagement.editor.EditorConfigEditorProvider.Companion.MAX_PREVIEW_LENGTH
import org.editorconfig.configmanagement.editor.EditorConfigEditorProvider.Companion.getLanguage
import org.editorconfig.language.filetype.EditorConfigFileType
import org.editorconfig.settings.EditorConfigSettings
import java.io.IOException

private val mainEditorProvider = PsiAwareTextEditorProvider()

internal class EditorConfigEditorProvider : AsyncFileEditorProvider {
  companion object {
    const val MAX_PREVIEW_LENGTH: Int = 10000

    fun getLanguage(virtualFile: VirtualFile): Language? {
      val fileType = virtualFile.fileType
      return if (fileType is LanguageFileType) fileType.language else null
    }
  }

  override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder = MyEditorBuilder(project, file)

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return FileTypeRegistry.getInstance().isFileOfType(file, EditorConfigFileType)
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor = MyEditorBuilder(project, file).build()

  override fun getEditorTypeId(): String = EDITOR_TYPE_ID

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private class MyEditorBuilder(val project: Project, val file: VirtualFile) : AsyncFileEditorProvider.Builder() {
  private val encodings: Set<String> = EditorConfigStatusListener.extractEncodings(project, file)

  override fun build(): FileEditor {
    val result: FileEditor
    val contextFile = EditorConfigPreviewManager.getInstance(project).getAssociatedPreviewFile(file)
    if (contextFile != null && CodeStyle.getSettings(project).getCustomSettings(EditorConfigSettings::class.java).ENABLED) {
      val document = EditorFactory.getInstance().createDocument(getPreviewText(contextFile))
      val disposable = Disposer.newDisposable()
      val previewFile = EditorConfigPreviewFile(project, contextFile, document, disposable)
      val previewEditor = createPreviewEditor(document, previewFile)
      val ecTextEditor = TextEditorProvider.getInstance().createEditor(project, file) as TextEditor
      result = EditorConfigEditorWithPreview(file, project, ecTextEditor, previewEditor)
      Disposer.register(result, disposable)
    }
    else {
      result = mainEditorProvider.createEditor(project, file)
    }
    val statusListener = EditorConfigStatusListener(project, file, encodings)
    CodeStyleSettingsManager.getInstance(project).subscribe(statusListener, result)
    return result
  }

  fun createPreviewEditor(document: Document, previewFile: EditorConfigPreviewFile): FileEditor {
    val previewEditor = EditorFactory.getInstance().createEditor(document, project)
    if (previewEditor is EditorEx) {
      val highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, previewFile)
      previewEditor.highlighter = highlighter
    }
    return EditorConfigPreviewFileEditor(previewEditor, previewFile)
  }
}

private const val EDITOR_TYPE_ID = "org.editorconfig.configmanagement.editor"

private fun getPreviewText(file: VirtualFile): String {
  if (file.length <= MAX_PREVIEW_LENGTH) {
    try {
      return StringUtil.convertLineSeparators(VfsUtilCore.loadText(file))
    }
    catch (e: IOException) {
      // Ignore
    }
  }

  val language = getLanguage(file)
  if (language != null) {
    val provider = LanguageCodeStyleSettingsProvider.forLanguage(language)
    if (provider != null) {
      val sample = provider.getCodeSample(LanguageCodeStyleSettingsProvider.SettingsType.INDENT_SETTINGS)
      if (sample != null) {
        return sample
      }
    }
  }
  return "No preview"
}
