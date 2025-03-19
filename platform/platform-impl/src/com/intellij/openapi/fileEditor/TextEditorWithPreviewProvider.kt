// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.TextEditorWithPreview.MyFileEditorState
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jdom.Attribute
import org.jdom.DataConversionException
import org.jdom.Element

private const val FIRST_EDITOR = "first_editor"
private const val SECOND_EDITOR = "second_editor"
private const val SPLIT_LAYOUT = "split_layout"
private const val VERTICAL_SPLIT = "is_vertical_split"

abstract class TextEditorWithPreviewProvider(private val previewProvider: FileEditorProvider): AsyncFileEditorProvider {
  private val mainProvider: TextEditorProvider = TextEditorProvider.getInstance()
  private val editorTypeId = createSplitEditorProviderTypeId(mainProvider.editorTypeId, previewProvider.editorTypeId)

  override fun accept(project: Project, file: VirtualFile): Boolean {
    return mainProvider.accept(project, file) && previewProvider.accept(project, file)
  }

  override fun acceptRequiresReadAction(): Boolean {
    return mainProvider.acceptRequiresReadAction() || previewProvider.acceptRequiresReadAction()
  }

  override fun createEditor(project: Project, file: VirtualFile): FileEditor {
    val first = mainProvider.createEditor(project, file)
    val second = previewProvider.createEditor(project, file)
    return createSplitEditor(first as TextEditor, second)
  }

  override fun getEditorTypeId(): String {
    return editorTypeId
  }

  final override suspend fun createFileEditor(
    project: Project,
    file: VirtualFile,
    document: Document?,
    editorCoroutineScope: CoroutineScope,
  ): FileEditor {
    val firstBuilder = createEditorBuilder(
      provider = mainProvider,
      project = project,
      file = file,
      document = document,
      editorCoroutineScope = editorCoroutineScope,
    )
    val secondBuilder = createEditorBuilder(
      provider = previewProvider,
      project = project,
      file = file,
      document = document,
      editorCoroutineScope = editorCoroutineScope,
    )
    return withContext(Dispatchers.EDT) {
      createSplitEditor(firstEditor = firstBuilder as TextEditor, secondEditor = secondBuilder)
    }
  }

  private fun readFirstProviderState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState? {
    val child = sourceElement.getChild(FIRST_EDITOR) ?: return null
    return mainProvider.readState(/* sourceElement = */ child, /* project = */ project, /* file = */ file)
  }

  private fun readSecondProviderState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState? {
    val child = sourceElement.getChild(SECOND_EDITOR) ?: return null
    return previewProvider.readState(/* sourceElement = */ child, /* project = */ project, /* file = */ file)
  }

  private fun writeFirstProviderState(state: FileEditorState?, project: Project, targetElement: Element) {
    val child = Element(FIRST_EDITOR)
    if (state != null) {
      mainProvider.writeState(state, project, child)
      targetElement.addContent(child)
    }
  }

  private fun writeSecondProviderState(state: FileEditorState?, project: Project, targetElement: Element) {
    val child = Element(SECOND_EDITOR)
    if (state != null) {
      previewProvider.writeState(state, project, child)
      targetElement.addContent(child)
    }
  }

  override fun readState(sourceElement: Element, project: Project, file: VirtualFile): FileEditorState {
    val firstState = readFirstProviderState(sourceElement, project, file)
    val secondState = readSecondProviderState(sourceElement, project, file)
    val layoutState = readSplitLayoutState(sourceElement)
    val isVerticalSplit = sourceElement.getAttribute(VERTICAL_SPLIT)?.booleanValue(false) ?: false
    return MyFileEditorState(layoutState, firstState, secondState, isVerticalSplit)
  }

  private fun Attribute.booleanValue(default: Boolean): Boolean {
    try {
      return booleanValue
    }
    catch (_: DataConversionException) {
      return default
    }
  }

  override fun writeState(state: FileEditorState, project: Project, targetElement: Element) {
    if (state is MyFileEditorState) {
      writeFirstProviderState(state.firstState, project, targetElement)
      writeSecondProviderState(state.secondState, project, targetElement)
      writeSplitLayoutState(state.splitLayout, targetElement)
      targetElement.setAttribute(VERTICAL_SPLIT, state.isVerticalSplit.toString())
    }
  }

  private fun readSplitLayoutState(sourceElement: Element): TextEditorWithPreview.Layout? {
    val value = sourceElement.getAttribute(SPLIT_LAYOUT)?.value
    return TextEditorWithPreview.Layout.entries.find { it.name == value }
  }

  private fun writeSplitLayoutState(layout: TextEditorWithPreview.Layout?, targetElement: Element) {
    val value = layout?.name ?: return
    targetElement.setAttribute(SPLIT_LAYOUT, value)
  }

  protected open fun createSplitEditor(firstEditor: TextEditor, secondEditor: FileEditor): FileEditor {
    return TextEditorWithPreview(firstEditor, secondEditor)
  }

  override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private suspend fun createEditorBuilder(
  provider: FileEditorProvider,
  project: Project,
  file: VirtualFile,
  document: Document?,
  editorCoroutineScope: CoroutineScope,
): FileEditor {
  return if (provider is AsyncFileEditorProvider) {
    provider.createFileEditor(project, file, document, editorCoroutineScope = editorCoroutineScope)
  }
  else {
    withContext(Dispatchers.EDT) {
      provider.createEditor(project, file)
    }
  }
}

fun createSplitEditorProviderTypeId(first: String, second: String): String {
  return "split-provider[$first;$second]"
}
