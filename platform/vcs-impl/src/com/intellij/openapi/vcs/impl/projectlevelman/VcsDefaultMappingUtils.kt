// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.impl.projectlevelman

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile

internal object VcsDefaultMappingUtils {
  /**
   * Provides VCS roots where [projectRoots] are located using [rootChecker].
   *
   * [mappedDirs] are took into account during detection, so that direct mappings shouldn't be overwritten by <Project> mappings.
   * E.g. if directory `/project` has direct mapping for Hg and there is `/project/.git` folder
   * (that is going to be detected as <Project> mapping),
   * `/project` shouldn't be returned as <Project> mapping. It should stay as Hg direct mapping.
   * So, already mapped files and directories from [mappedDirs] are excluded from the result.
   *
   * NB: if [rootChecker]'s [com.intellij.openapi.vcs.VcsRootChecker.areChildrenValidMappings]
   * is true then a result may contain directories under [project] but not "pure" VCS roots.
   * Example: for `root/svn_root_dir/project_root_dir` if `svn_root_dir` is not under project
   * `project_dir` will be returned as VCS root, not `svn_root_dir`.
   * It is needed to detect changes only under `project_root_dir`, not under `svn_root_dir/unrelated_project`
   *
   * @param projectRoots files and directories that are a part of the [project]
   * @param mappedDirs directories that were already mapped with VCS
   */
  @JvmStatic
  fun detectProjectMappings(
    project: Project,
    rootChecker: VcsRootChecker,
    projectRoots: Collection<VirtualFile>,
    mappedDirs: Set<VirtualFile>
  ): Set<VirtualFile> {
    return VcsDefaultMappingDetector(project, rootChecker).detectProjectMappings(projectRoots, mappedDirs)
  }
}

private class VcsDefaultMappingDetector(
  private val project: Project,
  private val rootChecker: VcsRootChecker
) {
  private val fileIndex = ProjectFileIndex.getInstance(project)

  private val checkedDirs = mutableMapOf<VirtualFile, Boolean>()

  fun detectProjectMappings(
    projectRoots: Collection<VirtualFile>,
    mappedDirs: Set<VirtualFile>
  ): Set<VirtualFile> {
    for (dir in mappedDirs) {
      checkedDirs[dir] = true
    }

    val vcsRoots = mutableSetOf<VirtualFile>()
    for (projectRoot in projectRoots) {
      val root = detectVcsForProjectRoot(projectRoot)
      if (root != null) {
        vcsRoots.add(root)
      }
    }

    vcsRoots.removeAll(mappedDirs) // do not report known mappings
    return vcsRoots
  }

  private fun detectVcsForProjectRoot(projectRoot: VirtualFile): VirtualFile? {
    for (file in generateSequence(projectRoot) { it.parent }) {
      if (isVcsRoot(file)) {
        return file
      }

      val parent = file.parent
      if (parent != null && !isUnderProject(parent)) {
        if (rootChecker.areChildrenValidMappings() &&
            isUnderVcsRoot(parent)) {
          return file
        }
        else {
          return null
        }
      }
    }

    return null
  }

  private fun isUnderVcsRoot(file: VirtualFile): Boolean {
    return generateSequence(file) { it.parent }.any { isVcsRoot(it) }
  }

  private fun isVcsRoot(file: VirtualFile): Boolean {
    ProgressManager.checkCanceled()
    return checkedDirs.computeIfAbsent(file) { key -> rootChecker.isRoot(key) }
  }

  private fun isUnderProject(f: VirtualFile): Boolean {
    return runReadAction {
      if (project.isDisposed) {
        throw ProcessCanceledException()
      }
      fileIndex.isInContent(f)
    }
  }
}