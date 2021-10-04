// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.roots.AdditionalLibraryRootsListener
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VirtualFile

internal class ModuleVcsDetector(private val project: Project) {
  private val vcsManager by lazy(LazyThreadSafetyMode.NONE) { ProjectLevelVcsManagerImpl.getInstanceImpl(project) }

  private fun startDetection() {
    val busConnection = project.messageBus.connect()

    val moduleListener = MyModuleListener()
    busConnection.subscribe(ProjectTopics.MODULES, moduleListener)
    busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, moduleListener)

    if (vcsManager.needAutodetectMappings()) {
      val initialDetectionListener = InitialMappingsDetectionListener()
      busConnection.subscribe(ProjectTopics.PROJECT_ROOTS, initialDetectionListener)
      busConnection.subscribe(AdditionalLibraryRootsListener.TOPIC, initialDetectionListener)

      autoDetectVcsMappings(true)
    }
  }

  private fun autoDetectVcsMappings(tryMapPieces: Boolean) {
    if (vcsManager.haveDefaultMapping() != null) return

    val usedVcses = mutableSetOf<AbstractVcs?>()
    val detectedRoots = mutableSetOf<Pair<VirtualFile, AbstractVcs>>()

    val roots = ModuleManager.getInstance(project).modules.asSequence()
      .flatMap { it.rootManager.contentRoots.asSequence() }
      .filter { it.isDirectory }.distinct().toList()
    for (root in roots) {
      val moduleVcs = vcsManager.findVersioningVcs(root)
      if (moduleVcs != null) {
        detectedRoots.add(Pair(root, moduleVcs))
      }
      usedVcses.add(moduleVcs) // put 'null' for unmapped module
    }

    val commonVcs = usedVcses.singleOrNull()
    if (commonVcs != null) {
      // Remove existing mappings that will duplicate added <Project> mapping.
      val rootPaths = roots.map { it.path }.toSet()
      val additionalMappings = vcsManager.directoryMappings.filter { it.directory !in rootPaths }

      vcsManager.setAutoDirectoryMappings(additionalMappings + VcsDirectoryMapping.createDefault(commonVcs.name))
    }
    else if (tryMapPieces) {
      val newMappings = detectedRoots.map { (root, vcs) -> VcsDirectoryMapping(root.path, vcs.name) }
      vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + newMappings)
    }
  }

  private fun autoDetectModuleVcsMapping(module: Module) {
    if (vcsManager.haveDefaultMapping() != null) return

    val newMappings = mutableListOf<VcsDirectoryMapping>()
    module.rootManager.contentRoots
      .filter { it.isDirectory }
      .forEach { file ->
        val vcs = vcsManager.findVersioningVcs(file)
        if (vcs != null && vcs !== vcsManager.getVcsFor(file)) {
          newMappings.add(VcsDirectoryMapping(file.path, vcs.name))
        }
      }

    if (newMappings.isNotEmpty()) {
      vcsManager.setAutoDirectoryMappings(vcsManager.directoryMappings + newMappings)
    }
  }

  private inner class MyModuleListener : ModuleRootListener, ModuleListener {
    private val mappingsForRemovedModules: MutableList<VcsDirectoryMapping> = mutableListOf()

    override fun beforeRootsChange(event: ModuleRootEvent) {
      mappingsForRemovedModules.clear()
    }

    override fun beforeModuleRemoved(project: Project, module: Module) {
      mappingsForRemovedModules.addAll(getMappings(module))
    }

    override fun moduleAdded(project: Project, module: Module) {
      mappingsForRemovedModules.removeAll(getMappings(module))

      autoDetectModuleVcsMapping(module)
    }

    override fun rootsChanged(event: ModuleRootEvent) {
      mappingsForRemovedModules.forEach { mapping -> vcsManager.removeDirectoryMapping(mapping) }
      mappingsForRemovedModules.clear()
    }

    private fun getMappings(module: Module): List<VcsDirectoryMapping> {
      return module.rootManager.contentRoots
        .filter { it.isDirectory }
        .mapNotNull { root -> vcsManager.directoryMappings.firstOrNull { it.directory == root.path } }
    }
  }

  private inner class InitialMappingsDetectionListener : ModuleRootListener, AdditionalLibraryRootsListener {
    override fun rootsChanged(event: ModuleRootEvent) {
      onRootsChanged()
    }

    override fun libraryRootsChanged(presentableLibraryName: String?,
                                     oldRoots: MutableCollection<out VirtualFile>,
                                     newRoots: MutableCollection<out VirtualFile>,
                                     libraryNameForDebug: String) {
      onRootsChanged()
    }

    private fun onRootsChanged() {
      if (vcsManager.needAutodetectMappings()) {
        autoDetectVcsMappings(false)
      }
    }
  }


  internal class MyPostStartUpActivity : StartupActivity.DumbAware {
    init {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        throw ExtensionNotApplicableException.INSTANCE
      }
    }

    override fun runActivity(project: Project) {
      project.service<ModuleVcsDetector>().startDetection()
    }
  }
}
