// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.openapi.module.Module

internal class ProjectStructure {
  private val sourceRoots: MutableMap<String, SourceRootBuilderImpl> = mutableMapOf()
  private val sdks: MutableMap<String, SdkBuilderImpl> = mutableMapOf()
  private val modules: MutableMap<String, ModuleBuilderImpl> = mutableMapOf()
  private val moduleFixtures: MutableMap<String, TestFixture<Module>> = mutableMapOf()

  fun findSourceRoot(sourceRootId: String): SourceRootBuilderImpl? =
    sourceRoots[sourceRootId]

  fun addSourceRoot(sourceRootId: String, sourceRootBuilder: SourceRootBuilderImpl) {
    if (sourceRoots.containsKey(sourceRootId)) {
      throw IllegalArgumentException("Source root ID '$sourceRootId' already exists in the registry!")
    }
    sourceRoots[sourceRootId] = sourceRootBuilder
  }

  fun addSdk(sdkName: String, sdkBuilder: SdkBuilderImpl) {
    sdks[sdkName] = sdkBuilder
  }

  fun getSdk(sdkName: String): SdkBuilderImpl? = sdks[sdkName]

  fun findModule(moduleName: String): ModuleBuilderImpl? = modules[moduleName]

  fun addModule(moduleName: String, module: ModuleBuilderImpl) {
    modules[moduleName] = module
  }

  fun findModuleFixture(name: String): TestFixture<Module>? = moduleFixtures[name]

  fun addModuleFixture(name: String, fixture: TestFixture<Module>) {
    moduleFixtures[name] = fixture
  }
}