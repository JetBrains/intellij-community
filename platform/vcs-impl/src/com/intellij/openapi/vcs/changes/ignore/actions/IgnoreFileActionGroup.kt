// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.actions

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ignore.cache.IgnorePatternsMatchedFilesCache
import com.intellij.openapi.vcs.changes.ignore.cache.PatternCache
import com.intellij.openapi.vcs.changes.ignore.lang.IgnoreFileType
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

open class IgnoreFileActionGroup(private val ignoreFileType: IgnoreFileType) :
  ActionGroup(
    VcsBundle.message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename),
    VcsBundle.message("vcs.add.to.ignore.file.action.group.description", ignoreFileType.ignoreLanguage.filename),
    ignoreFileType.icon
  ) {

  lateinit var actions: Collection<AnAction>

  override fun update(e: AnActionEvent) {
    val selectedFiles = e.getData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY)
    val project = e.getData<Project>(CommonDataKeys.PROJECT)
    val presentation = e.presentation

    if (project == null || selectedFiles == null) {
      presentation.isVisible = false
      return
    }

    val ignoreFiles = filterFiles(project, selectedFiles).map { findSuitableIgnoreFiles(project, it) }
    val resultedIgnoreFiles = ignoreFiles.flatten().toHashSet()

    for (files in ignoreFiles) {
      resultedIgnoreFiles.retainAll(files) //only take ignore files which is suitable for all selected files
    }

    actions = resultedIgnoreFiles.toActions(project)

    isPopup = actions.size > 1
    presentation.isVisible = actions.isNotEmpty()
  }

  override fun getChildren(e: AnActionEvent?) = actions.toTypedArray()

  private fun filterFiles(project: Project, files: Array<VirtualFile>) =
    files.filter { file ->
      VcsUtil.isFileUnderVcs(project, VcsUtil.getFilePath(file)) && !ChangeListManager.getInstance(project).isIgnoredFile(file)
    }

  private fun findSuitableIgnoreFiles(project: Project, file: VirtualFile): Collection<VirtualFile> {
    val language = ignoreFileType.ignoreLanguage
    PatternCache.getInstance(project).createPattern(language.filename, language.defaultSyntax)?.let { ignoreFileNamePattern ->
      val fileParent = file.parent
      return IgnorePatternsMatchedFilesCache.getInstance(project)
        .getFilesForPattern(ignoreFileNamePattern)
        .filter {
          fileParent == it.parent || fileParent != null && it.parent != null && VfsUtil.isAncestor(it.parent, fileParent, false)
        }
    }
    return hashSetOf()
  }

  private fun Collection<VirtualFile>.toActions(project: Project) =
    map { file ->
      IgnoreFileAction(file).apply {
        templatePresentation.apply {
          icon = ignoreFileType.icon
          VcsUtil.getVcsRootFor(project, file)?.let { vcsRoot ->
            val fileRelativeName = VfsUtil.getRelativePath(file, vcsRoot) ?: file.name
            text = if (this@toActions.size == 1) {
              VcsBundle.message("vcs.add.to.ignore.file.action.group.text", ignoreFileType.ignoreLanguage.filename)
            }
            else {
              fileRelativeName
            }
          }
        }
      }
    }
}
