// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.properties

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.openapi.vfs.findVirtualFileOrDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ConcurrentFactoryMap
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.properties.GradlePropertiesFile
import java.nio.file.Path

@ApiStatus.Internal
@RequiresReadLock
fun gradlePropertiesStream(place: PsiElement): Sequence<PropertiesFile> {
  val module = findModuleForPsiElement(place)
  val projectPath = ExternalSystemApiUtil.getExternalRootProjectPath(module) ?: return emptySequence()
  val project = place.project
  return GradlePropertiesFile.getPropertyPathsInBuildRoot(project, Path.of(projectPath)).asSequence()
    .mapNotNull { it.findVirtualFileOrDirectory() }
    .mapNotNull { it.findPsiFile(project) }
    .filterIsInstance<PropertiesFile>()
}

/**
 * Looks up a single Gradle property that applies to [file].
 *
 * The result is cached per file because callers such as inspection availability checks
 * are executed on every highlighting pass.
 */
@ApiStatus.Internal
@RequiresReadLock
fun findGradleProperty(file: PsiFile, name: String): String? {
  val properties = CachedValuesManager.getCachedValue(file) {
    val propertyByName = ConcurrentFactoryMap.createMap<String, String?> { propertyName ->
      gradlePropertiesStream(file).firstNotNullOfOrNull { it.findPropertyByKey(propertyName)?.value }
    }
    CachedValueProvider.Result.create(
      propertyByName,
      PsiModificationTracker.MODIFICATION_COUNT,
      ProjectRootManager.getInstance(file.project),
    )
  }
  return properties[name]
}
