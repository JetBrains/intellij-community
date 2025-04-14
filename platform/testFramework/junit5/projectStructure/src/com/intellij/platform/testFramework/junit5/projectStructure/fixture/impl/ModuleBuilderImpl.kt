// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ContentRootBuilder
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ModuleBuilder

internal class ModuleBuilderImpl(
  val moduleName: String,
  path: String,
  projectStructure: ProjectStructure,
) : DirectoryBuilderBase(path, projectStructure), ModuleBuilder {

  private val _contentRoots: MutableList<ContentRootBuilderImpl> = mutableListOf()
  private var _sdk: String? = null

  val contentRoots: List<ContentRootBuilderImpl> get() = _contentRoots
  val usedSdk: String? get() = _sdk

  override fun contentRoot(name: String, init: ContentRootBuilder.() -> Unit) {
    val contentRootPath = "$path/$name"
    val contentRootBuilder = ContentRootBuilderImpl(contentRootPath, projectStructure)
    _contentRoots.add(contentRootBuilder)
    contentRootBuilder.init()
  }

  override fun sharedSourceRoot(sourceRootId: String) {
    val originalSourceRootBuilder = projectStructure.findSourceRoot(sourceRootId)
                                    ?: throw IllegalArgumentException("Source root ID '$sourceRootId' does not exist in the registry!")

    val contentRoot = ContentRootBuilderImpl(
      path = originalSourceRootBuilder.path,
      projectStructure = projectStructure,
    )
    _contentRoots.add(contentRoot)

    val newSourceRoot = SourceRootBuilderImpl(name = originalSourceRootBuilder.name,
                                              path = originalSourceRootBuilder.path,
                                              projectStructure = projectStructure,
                                              isExisting = true)
    contentRoot.addSourceRoot(newSourceRoot)
  }

  override fun useSdk(name: String) {
    if (_sdk != null) {
      throw IllegalStateException("SDK for module $moduleName is already configured. The current value is $_sdk, new value is '$name'")
    }
    this._sdk = name
  }
}