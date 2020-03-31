// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.pluralize
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil.intersects
import com.intellij.vcsUtil.VcsUtil.getVcsFor
import kotlin.streams.asSequence

private fun VcsContext.getRoots(): Sequence<FilePath> = selectedFilePathsStream.asSequence()

private fun VcsContext.getCommonVcs(): AbstractVcs? {
  val project = project ?: return null
  return getRoots().mapNotNull { getVcsFor(project, it) }.distinct().singleOrNull()
}

open class CommonCheckinFilesAction : AbstractCommonCheckinAction() {
  override fun getActionName(dataContext: VcsContext): String {
    val actionName = dataContext.getCommonVcs()?.checkinEnvironment?.checkinOperationName

    return appendSubject(dataContext, actionName ?: message("vcs.command.name.checkin"))
  }

  private fun appendSubject(dataContext: VcsContext, checkinActionName: String): String {
    val roots = dataContext.getRoots().take(2).toList()
    if (roots.isEmpty()) return checkinActionName

    val messageKey = if (roots[0].isDirectory) "action.name.checkin.directory" else "action.name.checkin.file"
    return message(pluralize(messageKey, roots.size), checkinActionName)
  }

  override fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList {
    val manager = ChangeListManager.getInstance(project)
    val defaultChangeList = manager.defaultChangeList
    var result: LocalChangeList? = null

    for (root in getRoots(context)) {
      if (root.virtualFile == null) continue

      val changes = manager.getChangesIn(root)
      if (intersects(changes, defaultChangeList.changes)) return defaultChangeList

      result = changes.firstOrNull()?.let { manager.getChangeList(it) }
    }

    return result ?: defaultChangeList
  }

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean =
    dataContext.getRoots().any { isApplicableRoot(it, dataContext) }

  protected open fun isApplicableRoot(path: FilePath, dataContext: VcsContext): Boolean {
    val manager = ChangeListManagerImpl.getInstanceImpl(dataContext.project!!)
    val status = manager.getStatus(path)

    @Suppress("DEPRECATION")
    return (path.isDirectory || status != FileStatus.NOT_CHANGED) && status != FileStatus.IGNORED &&
           path.virtualFile?.let { isApplicableRoot(it, status, dataContext) } != false
  }

  @Deprecated("Use `isApplicableRoot(FilePath, VcsContext)` instead", ReplaceWith("isApplicableRoot()"))
  protected open fun isApplicableRoot(file: VirtualFile, status: FileStatus, dataContext: VcsContext): Boolean = true

  override fun getRoots(dataContext: VcsContext): Array<FilePath> = dataContext.selectedFilePaths

  override fun isForceUpdateCommitStateFromContext(): Boolean = true
}
