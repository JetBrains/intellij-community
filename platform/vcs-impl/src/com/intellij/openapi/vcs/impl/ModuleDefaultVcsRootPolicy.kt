// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.project.isDirectoryBased
import com.intellij.project.stateStore
import org.jetbrains.annotations.Nls

open class ModuleDefaultVcsRootPolicy(project: Project) : DefaultVcsRootPolicy(project) {
  init {
    val listener = MyModulesListener()
    val connection = myProject.messageBus.connect()
    connection.subscribe(ProjectTopics.MODULES, listener)
    connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener)
    connection.subscribe(AdditionalLibraryRootsListener.TOPIC, listener)
  }

  override fun getDefaultVcsRoots(): Collection<VirtualFile> {
    val result: MutableSet<VirtualFile> = HashSet()
    val baseDir = myProject.baseDir
    if (baseDir != null) {
      result.add(baseDir)
    }
    if (myProject.isDirectoryBased && baseDir != null) {
      val ideaDir = LocalFileSystem.getInstance().findFileByNioFile(myProject.stateStore.directoryStorePath!!)
      if (ideaDir != null) {
        result.add(ideaDir)
      }
    }

    // assertion for read access inside
    val modules = ReadAction.compute<Array<Module>, RuntimeException> { ModuleManager.getInstance(myProject).modules }
    for (module in modules) {
      val moduleRootManager = ModuleRootManager.getInstance(module)
      val files = moduleRootManager.contentRoots
      for (file in files) {
        if (file.isDirectory) {
          result.add(file)
        }
      }
    }
    return result
  }

  private inner class MyModulesListener : ModuleRootListener, ModuleListener, AdditionalLibraryRootsListener {
    override fun rootsChanged(event: ModuleRootEvent) {
      scheduleMappedRootsUpdate()
    }

    override fun moduleAdded(project: Project, module: Module) {
      scheduleMappedRootsUpdate()
    }

    override fun moduleRemoved(project: Project, module: Module) {
      scheduleMappedRootsUpdate()
    }

    override fun libraryRootsChanged(presentableLibraryName: @Nls String?,
                                     oldRoots: Collection<VirtualFile>,
                                     newRoots: Collection<VirtualFile>,
                                     libraryNameForDebug: String) {
      scheduleMappedRootsUpdate()
    }
  }
}