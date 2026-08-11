// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.java.properties.util

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.vfs.findVirtualFileOrDirectory
import com.intellij.psi.PsiElement
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import java.nio.file.Path

@ApiStatus.Internal
@RequiresReadLock
fun gradlePropertiesStream(place: PsiElement): Sequence<PropertiesFile> {
  val module = findModuleForPsiElement(place)
  val projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return emptySequence()
  val project = place.project
  return GradlePropertiesFile.getPropertyPaths(project, Path.of(projectPath)).asSequence()
    .mapNotNull { it.findVirtualFileOrDirectory() }
    .mapNotNull { it.findPsiFile(project) }
    .filterIsInstance<PropertiesFile>()
}
