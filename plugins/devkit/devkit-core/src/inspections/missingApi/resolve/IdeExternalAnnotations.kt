// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections.missingApi.resolve

import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * External annotations of IntelliJ API, which are to be included to the project.
 */
data class IdeExternalAnnotations(val annotationsBuild: BuildNumber, val annotationsRoot: VirtualFile)

private const val BUILD_TXT_FILE_NAME = "build.txt"

private val ANNOTATIONS_BUILD_NUMBER_KEY = Key.create<BuildNumber>("devkit.intellij.api.annotations.build.number")

fun getAnnotationsBuildNumber(annotationsRoot: VirtualFile): BuildNumber? {
  val cachedValue = annotationsRoot.getUserData(ANNOTATIONS_BUILD_NUMBER_KEY)
  if (cachedValue != null) {
    return cachedValue
  }
  val loadedValue = loadBuildNumber(annotationsRoot)
  annotationsRoot.putUserData(ANNOTATIONS_BUILD_NUMBER_KEY, loadedValue)
  return loadedValue
}

private fun loadBuildNumber(annotationsRoot: VirtualFile): BuildNumber? {
  val buildTxtFile = annotationsRoot.findFileByRelativePath(BUILD_TXT_FILE_NAME)
  if (buildTxtFile != null) {
    return BuildNumber.fromStringOrNull(VfsUtil.loadText(buildTxtFile))
  }
  return null
}

fun isAnnotationsRoot(virtualFile: VirtualFile) = getAnnotationsBuildNumber(virtualFile) != null
