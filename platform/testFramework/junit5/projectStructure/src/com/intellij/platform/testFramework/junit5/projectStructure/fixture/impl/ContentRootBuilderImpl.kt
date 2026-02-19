// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ContentRootBuilder
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.DirectoryBuilder

internal class ContentRootBuilderImpl(
  path: String,
  projectStructure: ProjectStructure,
) : DirectoryBuilderBase(path, projectStructure), ContentRootBuilder {
  private val _sourceRoots: MutableList<SourceRootBuilderImpl> = mutableListOf()

  val sourceRoots: List<SourceRootBuilderImpl> get() = _sourceRoots

  fun addSourceRoot(sourceRoot: SourceRootBuilderImpl) {
    _sourceRoots.add(sourceRoot)
  }

  override fun sourceRoot(
    name: String,
    sourceRootId: String?,
    init: DirectoryBuilder.() -> Unit,
  ) {
    val sourceRootPath = "$path/$name"
    val sourceRootBuilder = SourceRootBuilderImpl(name, sourceRootPath, projectStructure).apply(init)
    _sourceRoots.add(sourceRootBuilder)

    if (sourceRootId != null) {
      projectStructure.addSourceRoot(sourceRootId, sourceRootBuilder)
    }
  }

  override fun sharedSourceRoot(sourceRootId: String) {
    val sharedSourceRootBuilder = projectStructure.findSourceRoot(sourceRootId)
                                  ?: throw IllegalArgumentException("Source root ID '$sourceRootId' does not exist in the registry!")
    val newSourceRoot = SourceRootBuilderImpl(name = sharedSourceRootBuilder.name,
                                              path = sharedSourceRootBuilder.path,
                                              projectStructure = projectStructure,
                                              isExisting = true)
    _sourceRoots.add(newSourceRoot)
  }
}