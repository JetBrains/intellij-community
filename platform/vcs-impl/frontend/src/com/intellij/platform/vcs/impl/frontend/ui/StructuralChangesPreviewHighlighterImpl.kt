// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.ui

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vcs.changes.ui.StructuralChangesPreviewHighlighter
import com.intellij.openapi.vcs.changes.ui.highlightStructuralPreview
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent

internal class StructuralChangesPreviewHighlighterImpl(private val project: Project) : StructuralChangesPreviewHighlighter, Disposable {
  @RequiresEdt
  override fun highlight(file: VirtualFile, range: TextRange, modificationStamp: Long): Disposable? {
    if (project.isDisposed || !file.isValid) return null
    val manager = FileEditorManager.getInstance(project)
    val textEditor = manager.selectedEditor as? TextEditor ?: return null
    val editor = textEditor.editor
    if (editor !== manager.selectedTextEditor || editor.isDisposed || editor.virtualFile != file ||
        editor.document.modificationStamp != modificationStamp) return null

    val composite = (manager as? FileEditorManagerImpl)?.getComposite(textEditor) ?: return null
    if (!composite.isPreview) return null
    val markup = highlightStructuralPreview(editor, range) ?: return null
    val resources = Disposer.newDisposable("Structural changes preview highlight")
    val lifetime = Disposable {
      val application = ApplicationManager.getApplication()
      if (application.isDispatchThread) Disposer.dispose(resources)
      else application.invokeLater({ Disposer.dispose(resources) }, ModalityState.any())
    }
    Disposer.register(this, lifetime)
    Disposer.register(resources, markup)
    val clear = Runnable { Disposer.dispose(lifetime) }

    IdeEventQueue.getInstance().addPostprocessor({
      if (!composite.isPreview) clear.run()
      false
    }, resources)
    editor.caretModel.addCaretListener(object : CaretListener {
      override fun caretPositionChanged(event: CaretEvent) = clear.run()
      override fun caretAdded(event: CaretEvent) = clear.run()
      override fun caretRemoved(event: CaretEvent) = clear.run()
    }, resources)
    editor.selectionModel.addSelectionListener(object : SelectionListener {
      override fun selectionChanged(event: SelectionEvent) = clear.run()
    }, resources)
    editor.document.addDocumentListener(object : DocumentListener {
      override fun beforeDocumentChange(event: DocumentEvent) = clear.run()
    }, resources)
    editor.addEditorMouseListener(object : EditorMouseListener {
      override fun mousePressed(event: EditorMouseEvent) = clear.run()
    }, resources)
    val keyListener = object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) = clear.run()
    }
    editor.contentComponent.addKeyListener(keyListener)
    Disposer.register(resources) { editor.contentComponent.removeKeyListener(keyListener) }

    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorReleased(event: EditorFactoryEvent) {
        if (event.editor === editor) clear.run()
      }
    }, resources)
    project.messageBus.connect(resources).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        if (event.newEditor !== textEditor) clear.run()
      }
    })
    ApplicationManager.getApplication().messageBus.connect(resources).subscribe(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        if (event.getData(CommonDataKeys.EDITOR) === editor) clear.run()
      }

      override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
        if (CommonDataKeys.EDITOR.getData(dataContext) === editor) clear.run()
      }
    })
    return lifetime
  }

  override fun dispose() {}
}
