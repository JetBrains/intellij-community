// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.openapi.vcs.VcsActions
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.actions.diff.ShowDiffAction
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.util.PlatformIcons
import com.intellij.vcsUtil.VcsUtil
import java.awt.BorderLayout
import java.io.File
import javax.swing.Icon
import javax.swing.JPanel

const val TOOLWINDOW_ID = "Repositories"

fun showRepositoryBrowser(project: Project, root: AbstractVcsVirtualFile, localRoot: VirtualFile, title: String) {
  val toolWindowManager = ToolWindowManager.getInstance(project)
  val repoToolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID) ?: registerRepositoriesToolWindow(toolWindowManager, project)

  for (content in repoToolWindow.contentManager.contents) {
    val component = content.component as? RepositoryBrowserPanel ?: continue
    if (component.root == root) {
      repoToolWindow.contentManager.setSelectedContent(content)
      return
    }
  }

  val contentPanel = RepositoryBrowserPanel(project, root, localRoot)

  val content = ContentFactory.SERVICE.getInstance().createContent(contentPanel, title, true)
  repoToolWindow.contentManager.addContent(content)
  repoToolWindow.contentManager.setSelectedContent(content, true)
  repoToolWindow.activate(null)
}

private fun registerRepositoriesToolWindow(toolWindowManager: ToolWindowManager, project: Project): ToolWindow {
  val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.LEFT, project, true)
  ContentManagerWatcher.watchContentManager(toolWindow, toolWindow.contentManager)
  return toolWindow
}

val REPOSITORY_BROWSER_DATA_KEY = DataKey.create<RepositoryBrowserPanel>("com.intellij.openapi.vcs.impl.RepositoryBrowserPanel")

class RepositoryBrowserPanel(
  val project: Project,
  val root: AbstractVcsVirtualFile,
  val localRoot: VirtualFile
) : JPanel(BorderLayout()), DataProvider, Disposable {
  private val fileSystemTree: FileSystemTreeImpl

  init {
    val fileChooserDescriptor = object : FileChooserDescriptor(true, false, false, false, false, true) {
      override fun getRoots(): List<VirtualFile> = listOf(root)

      override fun getIcon(file: VirtualFile): Icon? {
        if (file.isDirectory) {
          return PlatformIcons.FOLDER_ICON
        }
        if (file is VcsVirtualFile) {
          val localPath = getLocalFilePath(file)
          val icon = FilePathIconProvider.EP_NAME.computeSafeIfAny { it.getIcon(localPath, project) }
          if (icon != null) return icon
        }
        return FileTypeManager.getInstance().getFileTypeByFileName(file.nameSequence).icon
      }
    }
    fileSystemTree = object : FileSystemTreeImpl(project, fileChooserDescriptor) {
      @Suppress("OverridingDeprecatedMember")
      override fun useNewAsyncModel() = true
    }
    fileSystemTree.addOkAction {
      val files = fileSystemTree.selectedFiles
      for (file in files) {
        FileEditorManager.getInstance(project).openFile(file, true)
      }
    }

    val actionGroup = DefaultActionGroup()
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE))
    actionGroup.add(ActionManager.getInstance().getAction(VcsActions.DIFF_AFTER_WITH_LOCAL))
    fileSystemTree.registerMouseListener(actionGroup)

    val scrollPane = ScrollPaneFactory.createScrollPane(fileSystemTree.tree)

    add(scrollPane, BorderLayout.CENTER)
  }

  override fun getData(dataId: String): Any? {
    return when {
      CommonDataKeys.VIRTUAL_FILE_ARRAY.`is`(dataId) -> fileSystemTree.selectedFiles
      CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) ->
        fileSystemTree.selectedFiles
          .filter { !it.isDirectory }
          .map { OpenFileDescriptor(project, it) }
          .toTypedArray()
      REPOSITORY_BROWSER_DATA_KEY.`is`(dataId) -> this
      else -> null
    }
  }

  override fun dispose() {
    Disposer.dispose(fileSystemTree)
  }

  fun hasSelectedFiles() = fileSystemTree.selectedFiles.any { it is VcsVirtualFile }

  fun getSelectionAsChanges(): List<Change> {
    return fileSystemTree.selectedFiles
      .filterIsInstance<VcsVirtualFile>()
      .map { createChangeVsLocal(it) }
  }

  private fun createChangeVsLocal(file: VcsVirtualFile): Change {
    val repoRevision = VcsVirtualFileContentRevision(file)
    val localPath = getLocalFilePath(file)
    val localRevision = CurrentContentRevision(localPath)
    return Change(repoRevision, localRevision)
  }

  private fun getLocalFilePath(file: VcsVirtualFile): FilePath {
    val localFile = File(localRoot.path, file.path)
    return VcsUtil.getFilePath(localFile)
  }
}

class DiffRepoWithLocalAction : AnActionExtensionProvider {
  override fun isActive(e: AnActionEvent): Boolean {
    return e.getData(REPOSITORY_BROWSER_DATA_KEY) != null
  }

  override fun update(e: AnActionEvent) {
    val repoBrowser = e.getData(REPOSITORY_BROWSER_DATA_KEY) ?: return
    e.presentation.isEnabled = repoBrowser.hasSelectedFiles()
  }

  override fun actionPerformed(e: AnActionEvent) {
    val repoBrowser = e.getData(REPOSITORY_BROWSER_DATA_KEY) ?: return
    val changes = repoBrowser.getSelectionAsChanges()
    ShowDiffAction.showDiffForChange(repoBrowser.project, changes)
  }
}

class VcsVirtualFileContentRevision(private val vcsVirtualFile: VcsVirtualFile) : ContentRevision, ByteBackedContentRevision {
  override fun getContent(): String? {
    return contentAsBytes?.let { LoadTextUtil.getTextByBinaryPresentation(it, vcsVirtualFile).toString() }
  }

  override fun getContentAsBytes(): ByteArray? {
    return vcsVirtualFile.fileRevision?.loadContent()
  }

  override fun getFile(): FilePath {
    return RemoteFilePath(vcsVirtualFile.path, vcsVirtualFile.isDirectory)
  }

  override fun getRevisionNumber(): VcsRevisionNumber {
    return vcsVirtualFile.fileRevision?.revisionNumber ?: VcsRevisionNumber.NULL
  }
}