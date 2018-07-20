// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ide.impl.ContentManagerWatcher
import com.intellij.ide.util.treeView.AbstractTreeBuilder
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileChooser.ex.RootFileElement
import com.intellij.openapi.fileChooser.impl.FileTreeBuilder
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.openapi.vcs.actions.VcsContextFactory
import com.intellij.openapi.vcs.changes.ByteBackedContentRevision
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.CurrentContentRevision
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
import java.awt.BorderLayout
import java.io.File
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel

const val TOOLWINDOW_ID = "Repositories"

fun showRepositoryBrowser(project: Project, root: AbstractVcsVirtualFile, localRoot: VirtualFile, title: String) {
  val toolWindowManager = ToolWindowManager.getInstance(project)
  val repoToolWindow = toolWindowManager.getToolWindow(TOOLWINDOW_ID)
                       ?: registerRepositoriesToolWindow(toolWindowManager)

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

private fun registerRepositoriesToolWindow(toolWindowManager: ToolWindowManager): ToolWindow {
  val toolWindow = toolWindowManager.registerToolWindow(TOOLWINDOW_ID, true, ToolWindowAnchor.LEFT)
  ContentManagerWatcher(toolWindow, toolWindow.contentManager)
  return toolWindow
}

val REPOSITORY_BROWSER_DATA_KEY = DataKey.create<RepositoryBrowserPanel>("com.intellij.openapi.vcs.impl.RepositoryBrowserPanel")

class RepositoryBrowserPanel(
  val project: Project,
  val root: AbstractVcsVirtualFile,
  val localRoot: VirtualFile
) : JPanel(BorderLayout()), DataProvider {
  private val fileSystemTree: FileSystemTreeImpl

  init {
    val fileChooserDescriptor = object : FileChooserDescriptor(true, false, false, false, false, true) {
      override fun getRoots(): List<VirtualFile> = listOf(root)

      override fun getIcon(file: VirtualFile): Icon? {
        if (file.isDirectory) {
          return PlatformIcons.FOLDER_ICON
        }
        return FileTypeManager.getInstance().getFileTypeByFileName(file.name).icon
      }
    }
    fileSystemTree = object : FileSystemTreeImpl(project, fileChooserDescriptor) {
      override fun createTreeBuilder(tree: JTree?,
                                     treeModel: DefaultTreeModel?,
                                     treeStructure: AbstractTreeStructure?,
                                     comparator: Comparator<NodeDescriptor<Any>>?,
                                     descriptor: FileChooserDescriptor?,
                                     onInitialized: Runnable?): AbstractTreeBuilder {
        return object : FileTreeBuilder(tree, treeModel, treeStructure, comparator, descriptor, onInitialized) {
          override fun isAutoExpandNode(nodeDescriptor: NodeDescriptor<*>): Boolean {
            return nodeDescriptor.element is RootFileElement
          }
        }
      }
    }
    fileSystemTree.addOkAction {
      val files = fileSystemTree.selectedFiles
      for (file in files) {
        FileEditorManager.getInstance(project).openFile(file, true)
      }
    }

    val actionGroup = DefaultActionGroup()
    actionGroup.add(ActionManager.getInstance().getAction(IdeActions.ACTION_EDIT_SOURCE))
    actionGroup.add(ActionManager.getInstance().getAction("Vcs.ShowDiffWithLocal"))
    fileSystemTree.registerMouseListener(actionGroup)

    val scrollPane = ScrollPaneFactory.createScrollPane(fileSystemTree.tree)

    add(scrollPane, BorderLayout.CENTER)
  }

  override fun getData(dataId: String?): Any? {
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

  fun hasSelectedFiles() = fileSystemTree.selectedFiles.any { it is VcsVirtualFile }

  fun getSelectionAsChanges(): List<Change> {
    return fileSystemTree.selectedFiles
      .filterIsInstance<VcsVirtualFile>()
      .map { createChangeVsLocal(it) }
  }

  private fun createChangeVsLocal(file: VcsVirtualFile): Change {
    val repoRevision = VcsVirtualFileContentRevision(file)
    val localPath = File(localRoot.path, file.path)
    val localRevision = CurrentContentRevision(VcsContextFactory.SERVICE.getInstance().createFilePathOn(localPath))
    return Change(repoRevision, localRevision)
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
    return vcsVirtualFile.fileRevision?.content
  }

  override fun getFile(): FilePath {
    return RemoteFilePath(vcsVirtualFile.path, vcsVirtualFile.isDirectory)
  }

  override fun getRevisionNumber(): VcsRevisionNumber {
    return vcsVirtualFile.fileRevision?.revisionNumber ?: VcsRevisionNumber.NULL
  }
}