// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.backend

import com.intellij.ide.ui.colors.rpcId
import com.intellij.ide.ui.icons.rpcId
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager
import com.intellij.openapi.fileEditor.impl.EditorTabPresentationUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vcs.FileStatusManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.VfsPresentationUtil
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.platform.recentFiles.shared.RecentFileKind
import com.intellij.platform.recentFiles.shared.SWITCHER_ELEMENTS_LIMIT
import com.intellij.platform.recentFiles.shared.SwitcherRpcDto
import com.intellij.problems.WolfTheProblemSolver
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IconUtil
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresReadLock
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.math.max

@RequiresReadLock
internal fun getFilesToShow(project: Project, recentFileKind: RecentFileKind, filesFromFrontendEditorSelectionHistory: List<VirtualFile>): List<VirtualFile> {
  val filesData = ArrayList<VirtualFile>()
  val editors = ArrayList<VirtualFile>()
  val addedFiles = LinkedHashSet<VirtualFile>()
  val unpinned = recentFileKind == RecentFileKind.RECENTLY_OPENED_UNPINNED
  if (unpinned) {
    for (hint in filesFromFrontendEditorSelectionHistory) {
      editors.add(hint)
    }

    for (editor in editors) {
      addIfNotNull(addedFiles, editor)
      filesData.add(editor)
      if (filesData.size >= SWITCHER_ELEMENTS_LIMIT) {
        break
      }
    }
  }

  if (filesData.size > 1) {
    return filesData
  }

  val filesForInit = when (recentFileKind) {
    RecentFileKind.RECENTLY_EDITED -> IdeDocumentHistory.getInstance(project).changedFiles
    RecentFileKind.RECENTLY_OPENED, RecentFileKind.RECENTLY_OPENED_UNPINNED -> getRecentFiles(project)
  }
  if (!filesForInit.isEmpty()) {
    val editorFileCount = editors.asSequence().distinct().count()
    val maxFiles = max(editorFileCount, filesForInit.size)
    val activeToolWindowsLimit =
      ToolWindowManagerEx.getInstanceEx(project).toolWindows
        .filter { it.isAvailable && it.isShowStripeButton }.size
    val minIndex = if (unpinned) filesForInit.size - activeToolWindowsLimit.coerceAtMost(maxFiles) else 0
    for (i in filesForInit.size - 1 downTo minIndex) {
      val info = filesForInit[i]
      var add = true
      if (!unpinned) {
        for (fileInfo in filesData) {
          if (fileInfo == info) {
            add = false
            break
          }
        }
      }
      if (add) {
        if (addIfNotNull(addedFiles, info)) {
          filesData.add(info)
        }
      }
    }
  }

  if (editors.size == 1 && (filesData.isEmpty() || editors[0] != filesData[0])) {
    if (addIfNotNull(addedFiles, editors[0])) {
      filesData.add(0, editors[0])
    }
  }
  return filesData
}

private fun <T : Any> addIfNotNull(targetCollection: MutableCollection<T>, item: T?): Boolean {
  if (item == null) return false
  return targetCollection.add(item)
}

private fun getRecentFiles(project: Project): List<VirtualFile> {
  val recentFiles = EditorHistoryManager.getInstance(project).fileList
  val openFiles = FileEditorManager.getInstance(project).openFiles
  val recentFilesSet = HashSet(recentFiles)
  val openFilesSet = openFiles.toHashSet()

  // add missing FileEditor tabs right after the last one, that is available via "Recent Files"
  var index = 0
  for (i in recentFiles.indices) {
    if (openFilesSet.contains(recentFiles[i])) {
      index = i
      break
    }
  }
  val result = ArrayList(recentFiles)
  result.addAll(index, openFiles.filter { !recentFilesSet.contains(it) })
  return result
}

internal fun createRecentFileViewModel(virtualFile: VirtualFile, project: Project): SwitcherRpcDto.File {
  ProgressManager.checkCanceled()
  val parentPath = virtualFile.parent?.path?.toNioPathOrNull()
  val sameNameFiles = FilenameIndex.getVirtualFilesByName(virtualFile.name, GlobalSearchScope.projectScope(project))
  val result = if (parentPath == null ||
                   parentPath.nameCount == 0 ||
                   sameNameFiles.size <= 1
  ) {
    ""
  }
  else {
    val filePath = parentPath.pathString
    val projectPath = project.basePath?.let { FileUtil.toSystemDependentName(it) }
    if (projectPath != null && FileUtil.isAncestor(projectPath, filePath, true)) {
      val locationRelativeToProjectDir = FileUtil.getRelativePath(projectPath, filePath, File.separatorChar)
      if (locationRelativeToProjectDir != null && Path(locationRelativeToProjectDir).nameCount != 0) locationRelativeToProjectDir
      else filePath
    }
    else if (FileUtil.isAncestor(SystemProperties.getUserHome(), filePath, true)) {
      val locationRelativeToUserHome = FileUtil.getLocationRelativeToUserHome(filePath)
      if (Path(locationRelativeToUserHome).nameCount != 0) locationRelativeToUserHome else filePath
    }
    else {
      filePath
    }
  }

  ProgressManager.checkCanceled()
  return SwitcherRpcDto.File(
    mainText = EditorTabPresentationUtil.getCustomEditorTabTitle(project, virtualFile) ?: virtualFile.presentableName,
    statusText = FileUtil.getLocationRelativeToUserHome(virtualFile.parent?.presentableUrl ?: virtualFile.presentableUrl),
    pathText = result,
    hasProblems = WolfTheProblemSolver.getInstance(project).isProblemFile(virtualFile),
    virtualFileId = virtualFile.rpcId(),
    iconId = IconUtil.getIcon(virtualFile, 0, project).rpcId(),
    backgroundColorId = VfsPresentationUtil.getFileBackgroundColor(project, virtualFile)?.rpcId(),
    foregroundTextColorId = FileStatusManager.getInstance(project).getStatus(virtualFile).color?.rpcId()
  )
}
