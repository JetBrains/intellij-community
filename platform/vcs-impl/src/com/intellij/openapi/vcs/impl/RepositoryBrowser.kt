// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.PlatformIcons
import java.awt.BorderLayout
import javax.swing.Icon
import javax.swing.JPanel

fun showRepositoryBrowser(project: Project, root: AbstractVcsVirtualFile, title: String) {
  val toolWindowManager = ToolWindowManager.getInstance(project)
  val repoToolWindow = toolWindowManager.getToolWindow("Repositories")
                         ?: toolWindowManager.registerToolWindow("Repositories", true, ToolWindowAnchor.LEFT)

  val contentPanel = createRepositoryBrowserPanel(project, root)

  val content = ContentFactory.SERVICE.getInstance().createContent(contentPanel, title, true)
  repoToolWindow.contentManager.addContent(content)
  repoToolWindow.activate(null)
}

private fun createRepositoryBrowserPanel(project: Project,
                                         root: AbstractVcsVirtualFile): JPanel {
  val fileChooserDescriptor = object : FileChooserDescriptor(true, false, false, false, false, true) {
    override fun getRoots(): List<VirtualFile> = listOf(root)

    override fun getIcon(file: VirtualFile): Icon? {
      if (file.isDirectory) {
        return PlatformIcons.FOLDER_ICON
      }
      return FileTypeManager.getInstance().getFileTypeByFileName(file.name).icon
    }
  }
  val fileSystemTree = FileSystemTreeImpl(project, fileChooserDescriptor)
  fileSystemTree.addOkAction {
    val files = fileSystemTree.selectedFiles
    for (file in files) {
      FileEditorManager.getInstance(project).openFile(file, true)
    }
  }

  val actionGroup = DefaultActionGroup()
  actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE))
  fileSystemTree.registerMouseListener(actionGroup)

  val scrollPane = ScrollPaneFactory.createScrollPane(fileSystemTree.tree)

  val dataProviderPanel: JPanel = object : JPanel(BorderLayout()), DataProvider {
    override fun getData(dataId: String?): Any? {
      return when {
        CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> fileSystemTree.selectedFiles
        CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> fileSystemTree.selectedFiles.map { OpenFileDescriptor(project, it) }.toTypedArray()
        else -> null
      }
    }
  }
  dataProviderPanel.add(scrollPane, BorderLayout.CENTER)
  return dataProviderPanel
}