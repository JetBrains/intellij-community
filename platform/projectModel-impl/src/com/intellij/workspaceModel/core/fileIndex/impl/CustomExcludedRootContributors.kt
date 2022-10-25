// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity

internal class CustomExcludedRootContributors(private val project: Project,
                                              private val rootFileSupplier: RootFileSupplier,
                                              private val excludedFileSets: MultiMap<VirtualFile, ExcludedFileSet>) {
  private val allRoots = HashSet<VirtualFile>()
  private var upToDate = false

  fun updateIfNeeded() {
    if (!upToDate) {
      updateCustomExcludedRoots()
      upToDate = true
    }
  }
  
  private fun updateCustomExcludedRoots() {
    for (root in allRoots) {
      excludedFileSets.removeValueIf(root) { it.entityReference === CustomExcludedRootsEntityReference }
    }
    allRoots.clear()

    DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project).forEach { policy -> 
      policy.excludeUrlsForProject.forEach { url ->
        val file = rootFileSupplier.findFileByUrl(url)
        if (file != null && RootFileSupplier.ensureValid(file, project, policy)) {
          excludedFileSets.putValue(file, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.ALL, CustomExcludedRootsEntityReference))
          allRoots.add(file)
        }
      }
      policy.excludeSdkRootsStrategy?.let { strategy ->
        val sdks = ModuleManager.getInstance(project).modules.mapNotNullTo(HashSet()) { ModuleRootManager.getInstance(it).sdk }
        val sdkClasses = sdks.flatMapTo(HashSet()) { it.rootProvider.getFiles(OrderRootType.CLASSES).asList() }
        sdks.forEach { sdk ->
          strategy.`fun`(sdk).forEach { root ->
            if (root !in sdkClasses) {
              val correctedRoot = rootFileSupplier.correctRoot(root, sdk, policy)
              if (correctedRoot != null) {
                excludedFileSets.putValue(correctedRoot, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.EXTERNAL,
                                                                                    CustomExcludedRootsEntityReference))
                allRoots.add(correctedRoot)
              }
            }
          }
        }
      }
      ModuleManager.getInstance(project).modules.forEach { module ->
        policy.getExcludeRootsForModule(ModuleRootManager.getInstance(module)).forEach { pointer ->
          val file = pointer.file
          if (file != null) {
            val correctedRoot = rootFileSupplier.correctRoot(file, module, policy)
            if (correctedRoot != null) {
              excludedFileSets.putValue(correctedRoot,
                                        ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.CONTENT, CustomExcludedRootsEntityReference))
              allRoots.add(correctedRoot)
            }
          }
        }
      }
    }
  }
  
  fun resetCache() {
    upToDate = false
  }
}

private object CustomExcludedRootsEntityReference : EntityReference<WorkspaceEntity>() {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
}