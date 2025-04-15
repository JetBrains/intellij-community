// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.asSafely
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import org.jetbrains.plugins.gradle.util.GradleConstants.GRADLE_PROPERTIES_FILE_NAME
import java.nio.file.Path

internal fun gradlePropertiesStream(place: PsiElement): Sequence<PropertiesFile> {
  val projectPath = place.getRootGradleProjectPath()?.let { Path.of(it) } ?: return emptySequence()
  return GradlePropertiesFile.getPropertyPaths(place.project, projectPath).asSequence()
    .mapNotNull { it.parent.getGradlePropertiesFile(place.project) }
}

private fun Path.getGradlePropertiesFile(project: Project): PropertiesFile? {
  val file = VfsUtil.findFile(this, false)?.findChild(GRADLE_PROPERTIES_FILE_NAME)
  return file?.let { PsiUtilCore.getPsiFile(project, it) }.asSafely<PropertiesFile>()
}
