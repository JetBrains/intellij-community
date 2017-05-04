/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.JdkOrderEntry
import com.intellij.openapi.roots.libraries.LibraryUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileProvider
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

fun displayUrlRelativeToProject(file: VirtualFile,
                                url: String,
                                project: Project,
                                includeFilePath: Boolean,
                                keepModuleAlwaysOnTheLeft: Boolean): String {
  var url = url
  val baseDir = project.baseDir
  if (baseDir != null && includeFilePath) {

    val projectHomeUrl = baseDir.presentableUrl
    if (url.startsWith(projectHomeUrl)) {
      url = "..." + url.substring(projectHomeUrl.length)
    }
  }

  if (SystemInfo.isMac && file.fileSystem is LocalFileProvider) {
    val fileForJar = (file.fileSystem as LocalFileProvider).getLocalVirtualFileFor(file)
    if (fileForJar != null) {
      val libraryEntry = LibraryUtil.findLibraryEntry(file, project)
      if (libraryEntry != null) {
        if (libraryEntry is JdkOrderEntry) {
          url = url + " - [" + libraryEntry.jdkName + "]"
        }
        else {
          url = url + " - [" + libraryEntry.presentableName + "]"
        }
      }
      else {
        url = url + " - [" + fileForJar.name + "]"
      }
    }
  }

  val module = ModuleUtilCore.findModuleForFile(file, project) ?: return url
  return if (!keepModuleAlwaysOnTheLeft && SystemInfo.isMac)
    url + " - [" + module.name + "]"
  else
    "[" + module.name + "] - " + url
}

fun getPresentableName(project: Project): String? {
  if (project.isDefault) {
    return project.name
  }

  val location = project.presentableUrl ?: return null

  var projectName = FileUtil.toSystemIndependentName(location)
  projectName = StringUtil.trimEnd(projectName, "/")

  val lastSlash = projectName.lastIndexOf('/')
  if (lastSlash >= 0 && lastSlash + 1 < projectName.length) {
    projectName = projectName.substring(lastSlash + 1)
  }

  if (StringUtil.endsWithIgnoreCase(projectName, ProjectFileType.DOT_DEFAULT_EXTENSION)) {
    projectName = projectName.substring(0, projectName.length - ProjectFileType.DOT_DEFAULT_EXTENSION.length)
  }

  projectName = projectName.toLowerCase(Locale.US).replace(':', '_') // replace ':' from windows drive names
  return projectName
}
