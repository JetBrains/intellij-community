package com.intellij.smartUpdate

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.actions.VcsContext
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeList
import com.intellij.openapi.vcs.update.CommonUpdateProjectAction
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class VcsUpdateStep: SmartUpdateStep {
  override fun performUpdateStep(project: Project, e: AnActionEvent?, onSuccess: () -> Unit) {
    val action = object: CommonUpdateProjectAction() {
      override fun onSuccess() {
        onSuccess.invoke()
      }

      public override fun actionPerformed(context: VcsContext) {
        super.actionPerformed(context)
      }
    }
    action.templatePresentation.text = SmartUpdateBundle.message("action.update.project.text")
    action.actionPerformed(UpdateProjectContext(project))
  }

  override fun isRequested(options: SmartUpdate.Options) = options.updateProject
}

class UpdateProjectContext(private val _project: Project): VcsContext {
  override fun getPlace(): String {
    TODO("Not yet implemented")
  }

  override fun getProject(): Project  = _project

  override fun getSelectedFile(): VirtualFile?  = null

  override fun getSelectedFiles(): Array<VirtualFile>  = VirtualFile.EMPTY_ARRAY

  override fun getEditor(): Editor?  = null

  override fun getSelectedFilesCollection(): MutableCollection<VirtualFile> = ArrayList()

  override fun getSelectedIOFiles(): Array<File> ? = null

  override fun getModifiers(): Int  = 0

  override fun getSelectedIOFile(): File?  = null

  override fun getSelectedFilePaths(): Array<FilePath> = emptyArray()

  override fun getSelectedFilePath(): FilePath? = null

  override fun getSelectedChangeLists(): Array<ChangeList>?  = null
  override fun getSelectedChanges(): Array<Change>?  = null

  override fun getActionName(): String  = ""
}