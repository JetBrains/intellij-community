// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ComponentNotRegistered")

package com.intellij.workspaceModel.ide

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleComponent

@State(name = "XXX")
class TestModuleComponent: ModuleComponent, PersistentStateComponent<TestModuleComponent> {
  var testString: String = ""

  override fun getState(): TestModuleComponent = this
  override fun loadState(state: TestModuleComponent) {
    testString = state.testString
  }

  companion object {
    fun getInstance(module: Module): TestModuleComponent = module.getComponent(TestModuleComponent::class.java)
  }
}