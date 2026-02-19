// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.indexer

import com.intellij.openapi.project.Project
import com.intellij.platform.eel.EelDescriptor

interface GradleLocalRepositoryIndexer {
  fun groups(descriptor: EelDescriptor): Collection<String>

  fun groups(descriptor: EelDescriptor, artifactId: String): Set<String>

  fun artifacts(descriptor: EelDescriptor): Set<String>

  fun artifacts(descriptor: EelDescriptor, groupId: String): Set<String>

  fun versions(descriptor: EelDescriptor, groupId: String, artifactId: String): Set<String>

  fun launchIndexUpdate(project: Project)
}