// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDirectoryMapping

internal class ModuleVcsDetector(private val project: Project) {
  private val vcsManager by lazy(LazyThreadSafetyMode.NONE) {
    (ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl)
  }

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      with(StartupManager.getInstance(project)) {
        registerStartupActivity {
          if (vcsManager.needAutodetectMappings()) {
            autoDetectVcsMappings(true)
          }
        }
        registerPostStartupActivity {
          val listener = MyModulesListener()
          project.messageBus.connect().apply {
            subscribe(ProjectTopics.MODULES, listener)
            subscribe(ProjectTopics.PROJECT_ROOTS, listener)
          }
        }
      }
    }
  }

  private inner class MyModulesListener : ModuleRootListener, ModuleListener {
    private val myMappingsForRemovedModules: MutableList<VcsDirectoryMapping> = mutableListOf()

    override fun beforeRootsChange(event: ModuleRootEvent) {
      myMappingsForRemovedModules.clear()
    }

    override fun rootsChanged(event: ModuleRootEvent) {
      myMappingsForRemovedModules.forEach { mapping -> vcsManager.removeDirectoryMapping(mapping) }
      // the check calculates to true only before user has done any change to mappings, i.e. in case modules are detected/added automatically
      // on start etc (look inside)
      if (vcsManager.needAutodetectMappings()) {
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
    if (vcsManager.haveDefaultMapping() != null) return

    val roots = ModuleManager.getInstance(project).modules.flatMap { it.rootManager.contentRoots.asIterable() }.distinct()
    val rootVcses = roots.mapNotNull { root -> vcsManager.findVersioningVcs(root)?.let { root to it } }
    // this case is only for project <-> one vcs.
    // Additional check for the case when just content root should be mapped, not all project
    if (rootVcses.size == 1) {
      val (root, vcs) = rootVcses.first()
      val projectBaseDir = project.baseDir
      if (projectBaseDir != null && projectBaseDir == root) {
        // here we put the project <-> vcs mapping, and removing all inside-project-roots mappings
        // (i.e. keeping all other mappings)
        val rootPaths = roots.map { it.path }.toSet()
        val additionalMappings = vcsManager.directoryMappings.filter { it.directory !in rootPaths }

        vcsManager.setAutoDirectoryMappings(additionalMappings + VcsDirectoryMapping.createDefault(vcs.name))
      }
    }
    else if (tryMapPieces) {
      val newMappings = rootVcses.map { (root, vcs) -> VcsDirectoryMapping(root.path, vcs.name) }
      vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + newMappings)
    }
  }

  private fun autoDetectModuleVcsMapping(module: Module) {
    if (vcsManager.haveDefaultMapping() != null) return

    val newMappings = mutableListOf<VcsDirectoryMapping>()
    for (file in module.rootManager.contentRoots) {
      val vcs = vcsManager.findVersioningVcs(file)
      if (vcs != null && vcs !== vcsManager.getVcsFor(file)) {
        newMappings.add(VcsDirectoryMapping(file.path, vcs.name))
      }
    }
    if (newMappings.isNotEmpty()) {
      vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + newMappings)
    }
  }

  private fun getMappings(module: Module): List<VcsDirectoryMapping> {
    return module.rootManager.contentRoots
      .mapNotNull { root -> vcsManager.directoryMappings.firstOrNull { it.directory == root.path } }
  }
}
