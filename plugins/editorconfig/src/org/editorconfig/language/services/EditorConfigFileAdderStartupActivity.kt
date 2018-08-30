// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modifyModules
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.util.EditorConfigVfsUtil

class EditorConfigFileAdderStartupActivity : StartupActivity {
  override fun runActivity(project: Project) {
    val shouldStop = EditorConfigRegistry.shouldStopAtProjectRoot()
    if (shouldStop) removeFiles(project)
    else addFiles(project)
  }

  private fun addFiles(project: Project) {
    if (EditorConfigVfsUtil.getEditorConfigFiles(project).isEmpty()) return
    val files = runReadAction { findExternalEditorConfigFiles(project) }
    if (files.isEmpty()) return
    project.modifyModules {
      for (file in files) {
        for (module in modules) {
          if (!ModuleRootManager.getInstance(module).fileIndex.isInContent(file)) {
            ModuleRootModificationUtil.addModuleLibrary(
              module,
              EditorConfigFileConstants.FILE_NAME,
              emptyList(),
              listOf(file.url)
            )
          }
        }
      }
    }
  }

  private fun removeFiles(project: Project) {
    val files = EditorConfigVfsUtil.getEditorConfigFiles(project)
    if (files.isEmpty()) return
    project.modifyModules {
      modules.forEach { module ->
        ModuleRootModificationUtil.updateModel(module) { model ->
          val table = model.moduleLibraryTable
          table.libraries.filter { it.name == EditorConfigFileConstants.FILE_NAME }
            .forEach(table::removeLibrary)
        }
      }
    }
  }

  private fun findExternalEditorConfigFiles(project: Project): List<VirtualFile> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val projectRoots = findProjectRoots(project)
    if (projectRoots.isEmpty()) return emptyList()
    val result = mutableListOf<VirtualFile>()

    fun findEditorConfigParents(file: VirtualFile) {
      val name = EditorConfigFileConstants.FILE_NAME
      var currentFolder: VirtualFile = file
      while (currentFolder.parent != null) {
        currentFolder = currentFolder.parent
        val child = currentFolder.findChild(name)
        if (child == null || child.isDirectory) continue
        result.add(child)
      }
    }

    projectRoots.forEach(::findEditorConfigParents)
    return result
  }

  private fun findProjectRoots(project: Project): List<VirtualFile> {
    ApplicationManager.getApplication().assertReadAccessAllowed()
    val result = mutableListOf<VirtualFile>()
    val projectFile = project.guessProjectDir()
    if (projectFile != null) {
      result.add(projectFile)
      ModuleManager.getInstance(project).modules.forEach { module ->
        ModuleRootManager.getInstance(module).contentRoots.forEach { root ->
          if (!VfsUtil.isAncestor(projectFile, root, false)) {
            result.add(root)
          }
        }
      }
    }
    return result
  }
}
