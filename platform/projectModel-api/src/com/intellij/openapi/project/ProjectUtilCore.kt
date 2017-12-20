/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("ProjectUtilCore")
package com.intellij.openapi.project

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PlatformUtils

fun displayUrlRelativeToProject(file: VirtualFile, url: String, project: Project, includeFilePath: Boolean, moduleOnTheLeft: Boolean): String {
  var result = url

  if (includeFilePath) {
    val projectHomeUrl = project.baseDir?.presentableUrl
    result = when {
      projectHomeUrl != null && result.startsWith(projectHomeUrl) -> "...${result.substring(projectHomeUrl.length)}"
      else -> FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
    }
  }

  if (file.fileSystem is LocalFileProvider) {
    @Suppress("DEPRECATION") val localFile = (file.fileSystem as LocalFileProvider).getLocalVirtualFileFor(file)
    if (localFile != null) {
      val libraryEntry = LibraryUtil.findLibraryEntry(file, project)
      when {
        libraryEntry is JdkOrderEntry -> return "$result [${libraryEntry.jdkName}]"
        libraryEntry != null -> return "$result [${libraryEntry.presentableName}]"
      }
    }
  }

  if (PlatformUtils.isCidr() || PlatformUtils.isRider()) // see PredefinedSearchScopeProviderImpl.getPredefinedScopes for the other place to fix.
    return result

  val module = ModuleUtilCore.findModuleForFile(file, project)
  return when {
    module == null -> result
    moduleOnTheLeft -> "[${module.name}] $result"
    else -> "$result [${module.name}]"
  }
}

interface ProjectFileStoreOptionManager {
  val isStoredExternally: Boolean
}

val Project.isExternalStorageEnabled: Boolean
  get() {
    val key = "com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager"
    val manager = picoContainer.getComponentInstance(key) as? ProjectFileStoreOptionManager ?: return false
    return manager.isStoredExternally || Registry.`is`("store.imported.project.elements.separately", false)
  }