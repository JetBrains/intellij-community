// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
abstract class DefaultVcsRootPolicy protected constructor(protected val myProject: Project) {
  /**
   * Return roots that belong to the project (ex: all content roots).
   * If 'Project' mapping is configured, all vcs roots for these roots will be put to the mappings.
   */
  abstract fun getDefaultVcsRoots(): Collection<VirtualFile>

  /**
   * A message describing the <Project> mapping in the settings view
   */
  val projectMappingDescription: @Nls String
    get() =
      if (myProject.isDirectoryBased) {
        val fileName = myProject.stateStore.directoryStorePath!!.fileName.toString()
        VcsBundle.message("settings.vcs.mapping.project.description.with.idea.directory", fileName)
      }
      else {
        VcsBundle.message("settings.vcs.mapping.project.description")
      }

  /**
   * A message describing the <Project> mapping in the mapping configuration dialog
   */
  val projectMappingInDialogDescription: @Nls String
    get() = if (myProject.isDirectoryBased) {
      val fileName = myProject.stateStore.directoryStorePath!!.fileName.toString()
      VcsBundle.message("settings.vcs.mapping.project.in.dialog.description.with.idea.directory", fileName)
    }
    else {
      VcsBundle.message("settings.vcs.mapping.project.description")
    }.let {
      @Suppress("HardCodedStringLiteral")
      StringUtil.escapeXmlEntities(ProjectBundle.message("project.roots.project.display.name") + ": " + it.replace('\n', ' '))
    }

  protected fun scheduleMappedRootsUpdate() {
    val vcsManager = ProjectLevelVcsManagerEx.getInstanceEx(myProject)
    if (!vcsManager.haveDefaultMapping().isNullOrEmpty()) {
      vcsManager.scheduleMappedRootsUpdate()
    }
  }

  /**
   * Schedules new scan for vcs in content roots. Should be called
   * when [DefaultVcsRootPolicy.getDefaultVcsRoots] collection is changed
   */
  protected fun scheduleRootsChangeProcessing(removed: Collection<VirtualFile>, added: Collection<VirtualFile>) {
    myProject.getService(ModuleVcsDetector::class.java).scheduleScanForNewContentRoots(removed, added)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): DefaultVcsRootPolicy {
      return project.getService(DefaultVcsRootPolicy::class.java)
    }
  }
}
