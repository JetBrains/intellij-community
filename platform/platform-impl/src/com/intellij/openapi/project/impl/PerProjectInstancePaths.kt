// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.util.io.isAncestor
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.name

class PerProjectInstancePaths {
  companion object {
    internal fun getSystemDir(projectStoreBaseDir: Path): Path {
      checkMode()
      @Suppress("TestOnlyProblems")
      return getSystemDir(PathManager.getSystemDir(), ProjectManagerEx.IS_CHILD_PROCESS, ::currentProjectStoreBaseDir, projectStoreBaseDir)
    }

    @TestOnly
    fun getSystemDir(currentSystem: Path,
                     currentChildProcess: Boolean,
                     currentProjectStoreBaseDir: () -> Path?,
                     newProjectStoreBaseDir: Path): Path {
      return adjustPathForNewProject(currentSystem, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
    }

    internal fun getConfigDir(projectStoreBaseDir: Path): Path {
      checkMode()
      @Suppress("TestOnlyProblems")
      return getConfigDir(PathManager.getConfigDir(), ProjectManagerEx.IS_CHILD_PROCESS, ::currentProjectStoreBaseDir, projectStoreBaseDir)
    }

    @TestOnly
    fun getConfigDir(currentConfig: Path,
                     currentChildProcess: Boolean,
                     currentProjectStoreBaseDir: () -> Path?,
                     newProjectStoreBaseDir: Path): Path {
      return adjustPathForNewProject(currentConfig, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
    }

    internal fun getPluginsDir(projectStoreBaseDir: Path): Path {
      checkMode()

      @Suppress("TestOnlyProblems")
      return getPluginsDir(
        PathManager.getPluginsDir(),
        PathManager.getConfigDir(),
        ProjectManagerEx.IS_CHILD_PROCESS,
        ::currentProjectStoreBaseDir,
        projectStoreBaseDir
      )
    }

    @TestOnly
    fun getPluginsDir(currentPlugins: Path,
                      currentConfig: Path,
                      currentChildProcess: Boolean,
                      currentProjectStoreBaseDir: () -> Path?,
                      newProjectStoreBaseDir: Path): Path {
      return if (currentConfig.isAncestor(currentPlugins)) {
        val newConfig = getConfigDir(currentConfig, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
        newConfig.resolve(currentConfig.relativize(currentPlugins))
      }
      else {
        adjustPathForNewProject(currentPlugins, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
      }
    }

    internal fun getLogDir(projectStoreBaseDir: Path): Path {
      checkMode()

      @Suppress("TestOnlyProblems")
      return getLogDir(
        PathManager.getLogDir(),
        PathManager.getSystemDir(),
        ProjectManagerEx.IS_CHILD_PROCESS,
        ::currentProjectStoreBaseDir,
        projectStoreBaseDir
      )
    }

    @TestOnly
    fun getLogDir(currentLog: Path,
                  currentSystem: Path,
                  currentChildProcess: Boolean,
                  currentProjectStoreBaseDir: () -> Path?,
                  newProjectStoreBaseDir: Path): Path {
      return if (currentSystem.isAncestor(currentLog)) {
        val newSystem = getSystemDir(currentSystem, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
        newSystem.resolve(currentSystem.relativize(currentLog))
      }
      else {
        adjustPathForNewProject(currentLog, currentChildProcess, currentProjectStoreBaseDir, newProjectStoreBaseDir)
      }
    }

    private fun checkMode() {
      require(ProjectManagerEx.IS_PER_PROJECT_INSTANCE_READY || ProjectManagerEx.IS_PER_PROJECT_INSTANCE_ENABLED) {
        "This function can only be used with allowed `process per project` mode"
      }
    }

    private fun currentProjectStoreBaseDir(): Path? {
      require(ProjectManagerEx.IS_CHILD_PROCESS) { "This function can only be used with enabled `process per project` mode" }

      val currentProject = ProjectManagerEx.getOpenProjects().singleOrNull()
      return currentProject?.basePath?.let { Paths.get(it) }
    }

    private fun adjustPathForNewProject(path: Path,
                                        currentChildProcess: Boolean,
                                        currentProjectStoreBaseDir: () -> Path?,
                                        newProjectStoreBaseDir: Path): Path {
      return if (currentChildProcess) {
        toPerProjectDir(toBaseDir(path, currentProjectStoreBaseDir()), newProjectStoreBaseDir)
      }
      else {
        toPerProjectDir(path, newProjectStoreBaseDir)
      }
    }

    private fun toPerProjectDir(base: Path, projectStoreBaseDir: Path): Path {
      return base / ProjectManagerEx.PER_PROJECT_SUFFIX / perProjectDirRelativePath(projectStoreBaseDir)
    }

    private fun perProjectDirRelativePath(projectStoreBaseDir: Path): Path {
      return Paths.get("/").relativize(projectStoreBaseDir)
    }

    private fun toBaseDir(perProjectDir: Path, projectStoreBaseDir: Path?): Path {
      return if (projectStoreBaseDir == null) {
        toBaseDir(perProjectDir)
      }
      else {
        toBaseDirFromProject(perProjectDir, projectStoreBaseDir)
      }
    }

    private fun toBaseDir(perProjectDir: Path): Path {
      var path: Path? = perProjectDir

      while (path != null && path.name != ProjectManagerEx.PER_PROJECT_SUFFIX) {
        path = path.parent
      }

      return requireNotNull(path?.parent) { "Illegal `process per project` path: $perProjectDir" }
    }

    private fun toBaseDirFromProject(perProjectDir: Path, projectStoreBaseDir: Path): Path {
      var path: Path? = perProjectDir
      var relativePath: Path? = perProjectDirRelativePath(projectStoreBaseDir)

      while (path != null && relativePath != null && path.name == relativePath.name) {
        path = path.parent
        relativePath = relativePath.parent
      }

      require(path != null && relativePath == null) { "Illegal `process per project` path: $perProjectDir, $projectStoreBaseDir" }

      return path.parent
    }
  }
}