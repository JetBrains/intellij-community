// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.impl

import com.intellij.ProjectTopics
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsDirectoryMapping
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

class ModuleVcsDetector(private val myProject: Project,
                        private val myVcsManager: ProjectLevelVcsManagerImpl,
                        startupManager: StartupManager) {
  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      startupManager.registerStartupActivity {
        if (myVcsManager.needAutodetectMappings()) {
          autoDetectVcsMappings(true)
        }
      }
      startupManager.registerPostStartupActivity {
        val connection = myProject.messageBus.connect()
        val listener = MyModulesListener()
        connection.subscribe(ProjectTopics.MODULES, listener)
        connection.subscribe(ProjectTopics.PROJECT_ROOTS, listener)
      }
    }
  }

  private inner class MyModulesListener : ModuleRootListener, ModuleListener {
    private val myMappingsForRemovedModules = ArrayList<Pair<String, VcsDirectoryMapping>>()

    override fun beforeRootsChange(event: ModuleRootEvent) {
      myMappingsForRemovedModules.clear()
    }

    override fun rootsChanged(event: ModuleRootEvent) {
      for (mapping in myMappingsForRemovedModules) {
        myVcsManager.removeDirectoryMapping(mapping.second)
      }
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
    val vcsMap = HashMap<VirtualFile, AbstractVcs<*>>()
    val moduleManager = ModuleManager.getInstance(myProject)
    for (module in moduleManager.modules) {
      val files = ModuleRootManager.getInstance(module).contentRoots
      for (file in files) {
        val contentRootVcs = myVcsManager.findVersioningVcs(file)
        if (contentRootVcs != null) {
          vcsMap[file] = contentRootVcs
        }
      }
    }
    val projectBaseDir = myProject.baseDir
    // this case is only for project <-> one vcs.
    // Additional check for the case when just content root should be mapped, not all project
    if (vcsMap.size == 1) {
      val folder = vcsMap.keys.iterator().next()
      val vcs = vcsMap[folder]
      if (vcs != null && projectBaseDir != null && projectBaseDir == folder) {
        // here we put the project <-> vcs mapping, and removing all inside-project-roots mappings
        // (i.e. keeping all other mappings)
        val modules = moduleManager.modules
        val contentRoots = HashSet<String>()
        for (module in modules) {
          val roots = ModuleRootManager.getInstance(module).contentRoots
          for (root in roots) {
            contentRoots.add(root.path)
          }
        }
        val vcsDirectoryMappings = ArrayList(myVcsManager.directoryMappings)
        val iterator = vcsDirectoryMappings.iterator()
        while (iterator.hasNext()) {
          val mapping = iterator.next()
          if (!contentRoots.contains(mapping.directory)) {
            iterator.remove()
          }
        }
        myVcsManager.setAutoDirectoryMapping("", vcs.name)
        for (mapping in vcsDirectoryMappings) {
          myVcsManager.removeDirectoryMapping(mapping)
        }
        myVcsManager.cleanupMappings()
      }
    }
    else if (tryMapPieces) {
      for ((file, vcs) in vcsMap) {
        myVcsManager.setAutoDirectoryMapping(file.path, if (vcs == null) "" else vcs.name)
      }
      myVcsManager.cleanupMappings()
    }
  }

  private fun autoDetectModuleVcsMapping(module: Module) {
    var mappingsUpdated = false
    val files = ModuleRootManager.getInstance(module).contentRoots
    for (file in files) {
      val vcs = myVcsManager.findVersioningVcs(file)
      if (vcs != null && vcs !== myVcsManager.getVcsFor(file)) {
        myVcsManager.setAutoDirectoryMapping(file.path, vcs.name)
        mappingsUpdated = true
      }
    }
    if (mappingsUpdated) {
      myVcsManager.cleanupMappings()
    }
  }

  private fun getMappings(module: Module): List<Pair<String, VcsDirectoryMapping>> {
    val result = ArrayList<Pair<String, VcsDirectoryMapping>>()
    val files = ModuleRootManager.getInstance(module).contentRoots
    val moduleName = module.name
    for (file in files) {
      for (mapping in myVcsManager.directoryMappings) {
        if (FileUtil.toSystemIndependentName(mapping.directory) == file.path) {
          result.add(Pair.create(moduleName, mapping))
          break
        }
      }
    }
    return result
  }
}
