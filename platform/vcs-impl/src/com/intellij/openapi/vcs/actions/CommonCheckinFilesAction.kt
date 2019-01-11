// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.getIfSingle
import com.intellij.vcsUtil.VcsUtil
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

open class CommonCheckinFilesAction : AbstractCommonCheckinAction() {
  override fun getActionName(dataContext: VcsContext): String {
    val actionName = Optional.ofNullable(dataContext.project)
      .map { project -> getCommonVcs(getRootsStream(dataContext), project) }
      .map { it!!.checkinEnvironment }
      .map { it!!.checkinOperationName }
      .orElse(VcsBundle.message("vcs.command.name.checkin"))

    return modifyCheckinActionName(dataContext, actionName)
  }

  private fun modifyCheckinActionName(dataContext: VcsContext, checkinActionName: String): String {
    var result = checkinActionName
    val roots = getRootsStream(dataContext).limit(2).collect(Collectors.toList())

    if (!roots.isEmpty()) {
      val messageKey = if (roots[0].isDirectory) "action.name.checkin.directory" else "action.name.checkin.file"
      result = VcsBundle.message(StringUtil.pluralize(messageKey, roots.size), checkinActionName)
    }

    return result
  }

  override fun getMnemonicsFreeActionName(context: VcsContext): String {
    return modifyCheckinActionName(context, VcsBundle.message("vcs.command.name.checkin.no.mnemonics"))
  }

  override fun getInitiallySelectedChangeList(context: VcsContext, project: Project): LocalChangeList? {
    val manager = ChangeListManager.getInstance(project)
    val defaultChangeList = manager.defaultChangeList
    var result: LocalChangeList? = null

    for (root in getRoots(context)) {
      if (root.virtualFile == null) continue

      val changes = manager.getChangesIn(root)
      if (defaultChangeList != null && containsAnyChange(defaultChangeList, changes)) {
        return defaultChangeList
      }
      result = changes.stream().findFirst().map { manager.getChangeList(it) }.orElse(null)
    }

    return ObjectUtils.chooseNotNull(result, defaultChangeList)
  }

  override fun approximatelyHasRoots(dataContext: VcsContext): Boolean {
    val manager = ChangeListManager.getInstance(dataContext.project!!)

    return getRootsStream(dataContext)
      .map { it.virtualFile }
      .filter { Objects.nonNull(it) }
      .anyMatch { file -> isApplicableRoot(file!!, manager.getStatus(file), dataContext) }
  }

  protected open fun isApplicableRoot(file: VirtualFile, status: FileStatus, dataContext: VcsContext): Boolean {
    return status !== FileStatus.UNKNOWN && status !== FileStatus.IGNORED
  }

  override fun getRoots(context: VcsContext): Array<FilePath> {
    return context.selectedFilePaths
  }

  protected fun getRootsStream(context: VcsContext): Stream<FilePath> {
    return context.selectedFilePathsStream
  }

  private fun containsAnyChange(changeList: LocalChangeList, changes: Collection<Change>): Boolean {
    return changes.stream().anyMatch { changeList.changes.contains(it) }
  }

  private fun getCommonVcs(roots: Stream<out FilePath>, project: Project): AbstractVcs<*>? {
    return roots.map { root -> VcsUtil.getVcsFor(project, root) }
      .filter { Objects.nonNull(it) }
      .distinct()
      .limit(Math.min(2, ProjectLevelVcsManager.getInstance(project).allActiveVcss.size).toLong())
      .getIfSingle()
  }
}
