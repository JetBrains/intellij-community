// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.settings.GradleLocalSettings
import org.jetbrains.plugins.gradle.util.PROPERTIES_FILE_NAME
import org.jetbrains.plugins.gradle.util.getGradleUserHomePropertiesPath
import java.nio.file.Path

internal fun gradlePropertiesStream(place: PsiElement): Sequence<PropertiesFile> = sequence {
  val externalRootProjectPath = place.getRootGradleProjectPath() ?: return@sequence
  val userHomePropertiesFile = getGradleUserHomePropertiesPath()?.parent?.toString()?.getGradlePropertiesFile(place.project)
  if (userHomePropertiesFile != null) {
    yield(userHomePropertiesFile)
  }
  val projectRootPropertiesFile = externalRootProjectPath.getGradlePropertiesFile(place.project)
  if (projectRootPropertiesFile != null) {
    yield(projectRootPropertiesFile)
  }
  val localSettings = GradleLocalSettings.getInstance(place.project)
  val installationDirectoryPropertiesFile = localSettings.getGradleHome(externalRootProjectPath)?.getGradlePropertiesFile(place.project)
  if (installationDirectoryPropertiesFile != null) {
    yield(installationDirectoryPropertiesFile)
  }
}

private fun String.getGradlePropertiesFile(project: Project): PropertiesFile? {
  val file = VfsUtil.findFile(Path.of(this), false)?.findChild(PROPERTIES_FILE_NAME)
  return file?.let { PsiUtilCore.getPsiFile(project, it) }.asSafely<PropertiesFile>()
}
