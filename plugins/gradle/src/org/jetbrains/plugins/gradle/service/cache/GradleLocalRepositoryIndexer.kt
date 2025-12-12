// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.cache

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor

interface GradleLocalRepositoryIndexer {
  fun groups(descriptor: EelDescriptor): Collection<String>

  fun artifacts(descriptor: EelDescriptor, groupId: String): Set<String>

  fun versions(descriptor: EelDescriptor, groupId: String, artifactId: String): Set<String>

  fun launchIndexUpdate(project: Project)
}

class GradleLocalRepositoryIndexerTestImpl : GradleLocalRepositoryIndexer {
  override fun groups(descriptor: EelDescriptor): Collection<String> = emptySet()

  override fun artifacts(descriptor: EelDescriptor, groupId: String): Set<String> = emptySet()

  override fun versions(descriptor: EelDescriptor, groupId: String, artifactId: String): Set<String> = emptySet()

  override fun launchIndexUpdate(project: Project) {}
}