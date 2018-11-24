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
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileProvider
import com.intellij.openapi.vfs.VirtualFile

fun displayUrlRelativeToProject(file: VirtualFile, url: String, project: Project, includeFilePath: Boolean, keepModuleAlwaysOnTheLeft: Boolean): String {
  var result = url
  val baseDir = project.baseDir
  if (baseDir != null && includeFilePath) {
    val projectHomeUrl = baseDir.presentableUrl
    if (result.startsWith(projectHomeUrl)) {
      result = "...${result.substring(projectHomeUrl.length)}"
    }
  }

  if (SystemInfo.isMac && file.fileSystem is LocalFileProvider) {
    val fileForJar = (file.fileSystem as LocalFileProvider).getLocalVirtualFileFor(file)
    if (fileForJar != null) {
      val libraryEntry = LibraryUtil.findLibraryEntry(file, project)
      if (libraryEntry != null) {
        if (libraryEntry is JdkOrderEntry) {
          result = "$result - [${libraryEntry.jdkName}]"
        }
        else {
          result = "$result - [${libraryEntry.presentableName}]"
        }
      }
      else {
        result = "$result - [${fileForJar.name}]"
      }
    }
  }

  val module = ModuleUtilCore.findModuleForFile(file, project) ?: return result
  return if (!keepModuleAlwaysOnTheLeft && SystemInfo.isMac) {
    "$result - [${module.name}]"
  }
  else {
    "[${module.name}] - $result"
  }
}

interface ProjectFileStoreOptionManager {
  val isStoredExternally: Boolean
}

val Project.isExternalStorageEnabled: Boolean
  get() {
    val manager = picoContainer.getComponentInstance("com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager") as? ProjectFileStoreOptionManager ?: return false
    return manager.isStoredExternally || Registry.`is`("store.imported.project.elements.separately", false) || IS_EXTERNAL_STORAGE_ENABLED
  }