// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VirtualFile
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

class CustomExcludedRootContributors(private val project: Project, private val rootFileSupplier: RootFileSupplier) {
  @Volatile
  private var customExcludedRoots: Object2IntMap<VirtualFile>? = null

  fun getCustomExcludedRootMask(file: VirtualFile): Int {
    val roots = getCustomExcludedRoots()
    return roots.getInt(file)
  }

  private fun getCustomExcludedRoots(): Object2IntMap<VirtualFile> {
    val roots = customExcludedRoots
    if (roots != null) return roots
    val newRoots = computeCustomExcludedRoots()
    customExcludedRoots = newRoots
    return newRoots
  }

  private fun computeCustomExcludedRoots(): Object2IntMap<VirtualFile> {
    val roots = Object2IntOpenHashMap<VirtualFile>()
    
    DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project).forEach { policy -> 
      policy.excludeUrlsForProject.forEach { url ->
        val file = rootFileSupplier.findFileByUrl(url)
        if (file != null && RootFileSupplier.ensureValid(file, project, policy)) {
          roots.put(file, WorkspaceFileKindMask.ALL)
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
                roots.put(correctedRoot, WorkspaceFileKindMask.EXTERNAL or roots.getInt(correctedRoot))
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
              roots.put(correctedRoot, WorkspaceFileKindMask.CONTENT or roots.getInt(correctedRoot))
            }
          }
        }
      }
    }
    
    AdditionalLibraryRootsProvider.EP_NAME.extensionList.forEach { provider ->
      provider.getAdditionalProjectLibraries(project).forEach { library ->
        library.excludedRoots.forEach { root ->
          val correctedRoot = rootFileSupplier.correctRoot(root, library, provider)
          if (correctedRoot != null) {
            roots.put(correctedRoot, WorkspaceFileKindMask.EXTERNAL or roots.getInt(correctedRoot))
          }
        }
      }
    }
    return roots
  }

  fun resetCache() {
    customExcludedRoots = null
  }
}