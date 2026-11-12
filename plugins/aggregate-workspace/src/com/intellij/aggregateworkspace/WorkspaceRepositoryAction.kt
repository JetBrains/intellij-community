package com.intellij.aggregateworkspace

import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.vfs.LocalFileSystem

abstract class WorkspaceRepositoryAction(
  private val repositoryName: String,
  private val repositoryUrl: String,
) : AnAction() {

  init {
    templatePresentation.text = repositoryName
    templatePresentation.description = "Open local checkout for $repositoryName"
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val propertyKey = "aggregate.workspace.checkout.$repositoryName"

    val saved = PropertiesComponent.getInstance(project).getValue(propertyKey)
    if (saved != null) {
      val savedDir = LocalFileSystem.getInstance().findFileByPath(saved)
      if (savedDir != null && savedDir.isDirectory) {
        ProjectUtil.openOrImport(savedDir.path, project, false)
        return
      }
    }

    val chooser = FileChooserDescriptorFactory.createSingleFolderDescriptor()
    val selected = FileChooser.chooseFile(chooser, project, null) ?: return
    PropertiesComponent.getInstance(project).setValue(propertyKey, selected.path)
    ProjectUtil.openOrImport(selected.path, project, false)
  }
}
