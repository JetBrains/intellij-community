// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.impl

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ModuleAttachListener
import java.io.File
import java.nio.file.Path

class VcsModuleAttachListener : ModuleAttachListener {
  override fun afterAttach(module: Module, primaryModule: Module?, imlFile: Path) {
    primaryModule ?: return

    val dotIdeaDirParent = imlFile.parent?.parent?.let { LocalFileSystem.getInstance().findFileByPath(it.toString()) }
    if (dotIdeaDirParent != null) {
      addVcsMapping(primaryModule, dotIdeaDirParent)
    }
  }

  override fun beforeDetach(module: Module) {
    removeVcsMapping(module)
  }

  private fun addVcsMapping(primaryModule: Module, addedModuleContentRoot: VirtualFile) {
    val project = primaryModule.project
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val mappings = vcsManager.directoryMappings
    if (mappings.size == 1) {
      val contentRoots = ModuleRootManager.getInstance(primaryModule).contentRoots
      // if we had one mapping for the root of the primary module and the added module uses the same VCS, change mapping to <Project Root>
      if (contentRoots.size == 1 && FileUtil.filesEqual(File(contentRoots[0].path), File(mappings[0].directory))) {
        val vcs = vcsManager.findVersioningVcs(addedModuleContentRoot)
        if (vcs != null && vcs.name == mappings[0].vcs) {
          vcsManager.directoryMappings = listOf(VcsDirectoryMapping.createDefault(vcs.name))
          return
        }
      }
    }
    val vcs = vcsManager.findVersioningVcs(addedModuleContentRoot)
    if (vcs != null) {
      val newMappings = ArrayList(mappings)
      newMappings.add(VcsDirectoryMapping(addedModuleContentRoot.path, vcs.name))
      vcsManager.directoryMappings = newMappings
    }
  }

  private fun removeVcsMapping(module: Module) {
    val project = module.project
    val vcsManager = ProjectLevelVcsManager.getInstance(project)
    val mappings = vcsManager.directoryMappings
    val newMappings = ArrayList(mappings)
    for (mapping in mappings) {
      for (root in ModuleRootManager.getInstance(module).contentRoots) {
        if (FileUtil.filesEqual(File(root.path), File(mapping.directory))) {
          newMappings.remove(mapping)
        }
      }
    }
    vcsManager.directoryMappings = newMappings
  }
}