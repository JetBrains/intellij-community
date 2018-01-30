// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ProjectUtilCore")
package com.intellij.openapi.project

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.util.io.FileUtil
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

val Project.isExternalStorageEnabled: Boolean
  get() {
    if (projectFilePath?.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) == true) {
      return false
    }

    val manager = ServiceManager.getService(this, ExternalStorageConfigurationManager::class.java) ?: return false
    return manager.isEnabled() || (ApplicationManager.getApplication()?.isUnitTestMode ?: false)
  }