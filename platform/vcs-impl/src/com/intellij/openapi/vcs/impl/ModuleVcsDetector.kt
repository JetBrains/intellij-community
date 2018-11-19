// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.vcs.VcsDirectoryMapping

class ModuleVcsDetector(private val myProject: Project,
                        private val myVcsManager: ProjectLevelVcsManagerImpl,
                        startupManager: StartupManager) {
  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      with(startupManager) {
        registerStartupActivity {
          if (myVcsManager.needAutodetectMappings()) {
            autoDetectVcsMappings(true)
          }
        }
        registerPostStartupActivity {
          val listener = MyModulesListener()
          myProject.messageBus.connect().apply {
            subscribe(ProjectTopics.MODULES, listener)
            subscribe(ProjectTopics.PROJECT_ROOTS, listener)
          }
        }
      }
    }
  }

  private inner class MyModulesListener : ModuleRootListener, ModuleListener {
    private val myMappingsForRemovedModules: MutableList<Pair<String, VcsDirectoryMapping>> = mutableListOf()

    override fun beforeRootsChange(event: ModuleRootEvent) {
      myMappingsForRemovedModules.clear()
    }

    override fun rootsChanged(event: ModuleRootEvent) {
      myMappingsForRemovedModules.forEach { (_, mapping) -> myVcsManager.removeDirectoryMapping(mapping) }
      // the check calculates to true only before user has done any change to mappings, i.e. in case modules are detected/added automatically
      // on start etc (look inside)
      if (myVcsManager.needAutodetectMappings()) {
        autoDetectVcsMappings(false)
      }
    }

    override fun moduleAdded(project: Project, module: Module) {
      myMappingsForRemovedModules.removeAll(getMappings(module))
      autoDetectModuleVcsMapping(module)
    }

    override fun beforeModuleRemoved(project: Project, module: Module) {
      myMappingsForRemovedModules.addAll(getMappings(module))
    }
  }

  private fun autoDetectVcsMappings(tryMapPieces: Boolean) {
    val roots = ModuleManager.getInstance(myProject).modules.flatMap { it.rootManager.contentRoots.asIterable() }.distinct()
    val rootVcses = roots.mapNotNull { root -> myVcsManager.findVersioningVcs(root)?.let { root to it } }
    // this case is only for project <-> one vcs.
    // Additional check for the case when just content root should be mapped, not all project
    if (rootVcses.size == 1) {
      val (root, vcs) = rootVcses.first()
      val projectBaseDir = myProject.baseDir
      if (projectBaseDir != null && projectBaseDir == root) {
        // here we put the project <-> vcs mapping, and removing all inside-project-roots mappings
        // (i.e. keeping all other mappings)
        val rootPaths = roots.map { it.path }.toSet()
        val redundantMappings = myVcsManager.directoryMappings.filter { it.directory in rootPaths }

        myVcsManager.setAutoDirectoryMapping("", vcs.name)
        redundantMappings.forEach { myVcsManager.removeDirectoryMapping(it) }
        myVcsManager.cleanupMappings()
      }
    }
    else if (tryMapPieces) {
      rootVcses.forEach { (root, vcs) -> myVcsManager.setAutoDirectoryMapping(root.path, vcs.name) }
      myVcsManager.cleanupMappings()
    }
  }

  private fun autoDetectModuleVcsMapping(module: Module) {
    var mappingsUpdated = false
    for (file in module.rootManager.contentRoots) {
      val vcs = myVcsManager.findVersioningVcs(file)
      if (vcs != null && vcs !== myVcsManager.getVcsFor(file, module)) {
        myVcsManager.setAutoDirectoryMapping(file.path, vcs.name)
        mappingsUpdated = true
      }
    }
    if (mappingsUpdated) {
      myVcsManager.cleanupMappings()
    }
  }

  private fun getMappings(module: Module): List<Pair<String, VcsDirectoryMapping>> {
    val moduleName = module.name
    return module.rootManager.contentRoots
      .mapNotNull { root -> myVcsManager.directoryMappings.firstOrNull { it.systemIndependentPath() == root.path } }
      .map { moduleName to it }
  }
}
