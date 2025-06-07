// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture.impl

import com.intellij.platform.testFramework.junit5.projectStructure.fixture.ModuleDependenciesBuilder

internal class ModuleDependenciesBuilderImpl(val moduleName: String, val projectStructure: ProjectStructure) : ModuleDependenciesBuilder {
  private var _sdk: String? = null
  private val _dependencies: MutableList<ModuleBuilderImpl> = mutableListOf()

  val usedSdk: String? get() = _sdk
  val dependencies: List<ModuleBuilderImpl> get() = _dependencies

  override fun useSdk(name: String) {
    if (_sdk != null) {
      throw IllegalStateException("SDK for module $moduleName is already configured. The current value is $_sdk, new value is '$name'")
    }
    this._sdk = name
  }


  override fun module(name: String) {
    projectStructure.findModule(name)?.let { module -> _dependencies.add(module)}
    ?: throw IllegalStateException("Module '$name' does not exist!")
  }
}