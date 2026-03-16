// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectUtilCore")

package com.intellij.openapi.project

import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil.ELLIPSIS
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.util.PathUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.ide.presentableName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@NlsSafe
fun displayUrlRelativeToProject(
  file: VirtualFile,
  @NlsSafe url: String,
  project: Project,
  isIncludeFilePath: Boolean,
  moduleOnTheLeft: Boolean,
): String {
  var result = url

  if (isIncludeFilePath) result = displayFilePath(project = project, url = result, file = file)

  val urlWithLibraryName = decorateWithLibraryName(file = file, project = project, result = result)
  if (urlWithLibraryName != null) {
    return urlWithLibraryName
  }

  // see PredefinedSearchScopeProviderImpl.getPredefinedScopes for the other place to fix.
  if (PlatformUtils.isCidr() || PlatformUtils.isRider()) {
    return result
  }

  return appendModuleName(file = file, project = project, result = result, moduleOnTheLeft = moduleOnTheLeft)
}

fun displayFilePath(project: Project, file: VirtualFile): @NlsSafe String {
  return displayFilePath(project = project, url = file.presentableUrl, file = file)
}

private fun displayFilePath(project: Project, url: String, file: VirtualFile): @NlsSafe String {
  val projectHomeUrl = PathUtil.toSystemDependentName(project.basePath)
  return when {
    projectHomeUrl != null && url.startsWith(projectHomeUrl) -> ELLIPSIS + url.substring(projectHomeUrl.length)
    else -> FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
  }
}

fun decorateWithLibraryName(file: VirtualFile, project: Project, result: String): String? {
  if (file.fileSystem.protocol == URLUtil.JAR_PROTOCOL) {
    val libs = ProjectFileIndex.getInstance(project).findContainingSdks(file).asSequence()
    val sdks = ProjectFileIndex.getInstance(project).findContainingLibraries(file).asSequence()
    val entity = (libs + sdks).firstOrNull()
    when (entity) {
      is SdkEntity -> return "${result} [${entity.name}]"
      is LibraryEntity -> return "${result} [${entity.presentableName}]"
    }
  }
  return null
}

fun appendModuleName(file: VirtualFile, project: Project, result: String, moduleOnTheLeft: Boolean): String {
  val module = ModuleUtilCore.findModuleForFile(file, project)
  return appendModuleName(module, result, moduleOnTheLeft)
}

fun appendModuleName(module: Module?, result: String, moduleOnTheLeft: Boolean): String {
  return when {
    module == null || ModuleManager.getInstance(module.project).modules.size == 1 -> result
    moduleOnTheLeft -> "[${module.name}] $result"
    else -> "$result [${module.name}]"
  }
}

private var enableExternalStorageByDefaultInTests = true

val Project.isExternalStorageEnabled: Boolean
  get() {
    if (projectFilePath?.endsWith(ProjectFileType.DOT_DEFAULT_EXTENSION) == true) {
      return false
    }

    val manager = ExternalStorageConfigurationManager.getInstance(this) ?: return false
    if (manager.isEnabled) {
      return true
    }

    val testMode = ApplicationManager.getApplication()?.isUnitTestMode ?: false
    return testMode && enableExternalStorageByDefaultInTests
  }

/**
 * By default, external storage is enabled in tests.
 * Wrap code which loads the project into this call to always use an explicit option value.
 */
@TestOnly
fun doNotEnableExternalStorageByDefaultInTests(action: () -> Unit) {
  doNotEnableExternalStorageByDefaultInTestsImpl(action)
}

/**
 * @see [doNotEnableExternalStorageByDefaultInTests]
 */
@ApiStatus.Internal
@TestOnly
suspend fun doNotEnableExternalStorageByDefaultInTestsSuspend(action: suspend () -> Unit) {
  doNotEnableExternalStorageByDefaultInTestsImpl {
    action()
  }
}

private inline fun doNotEnableExternalStorageByDefaultInTestsImpl(action: () -> Unit) {
  enableExternalStorageByDefaultInTests = false
  try {
    action()
  }
  finally {
    enableExternalStorageByDefaultInTests = true
  }
}