// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

internal class ProjectStructure {
  private val sourceRoots: MutableMap<String, SourceRootBuilderImpl> = mutableMapOf()

  fun findSourceRoot(sourceRootId: String): SourceRootBuilderImpl? =
    sourceRoots[sourceRootId]

  fun addSourceRoot(sourceRootId: String, sourceRootBuilder: SourceRootBuilderImpl) {
    if (sourceRoots.containsKey(sourceRootId)) {
      throw IllegalArgumentException("Source root ID '$sourceRootId' already exists in the registry!")
    }
    sourceRoots[sourceRootId] = sourceRootBuilder
  }
}