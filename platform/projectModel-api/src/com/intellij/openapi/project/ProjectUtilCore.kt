/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("ProjectUtilCore")
package com.intellij.openapi.project

import com.intellij.configurationStore.IS_EXTERNAL_STORAGE_ENABLED
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileProvider
import com.intellij.openapi.vfs.VirtualFile

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
    return manager.isStoredExternally || Registry.`is`("store.imported.project.elements.separately", false) || IS_EXTERNAL_STORAGE_ENABLED
  }