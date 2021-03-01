// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.editor

import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import training.lang.LangSupport
import training.learn.LearnBundle
import training.util.PerformActionUtil
import training.util.findLanguageSupport
import java.lang.ref.WeakReference
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

private class LearnProjectFileEditorListener(project: Project) : FileEditorManagerListener {
  private val ref by lazy { WeakReference(findLanguageSupport(project)) }
  private val langSupport: LangSupport? get() = ref.get()

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val langSupport = langSupport?: return

    val project = source.project
    if (ScratchUtil.isScratch(file)) {
      return
    }
    if (!langSupport.blockProjectFileModification(project, file)) {
      return
    }
    source.getAllEditors(file).forEach {
      ((it as? TextEditor)?.editor as? EditorEx)?.let { editorEx ->
        val listener = HyperlinkListener { event ->
          if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
            PerformActionUtil.performAction("CloseProject", editorEx, project, withWriteAccess = false)
          }
        }
        EditorModificationUtil.setReadOnlyHint(editorEx, LearnBundle.message("learn.project.read.only.hint"), listener)
        editorEx.isViewer = true
      }
    }
  }
}
