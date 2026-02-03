// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.assertion.moduleAssertion

import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import org.junit.jupiter.api.Assertions

object ExModuleOptionAssertions {

  fun assertExModuleOptions(module: ModuleEntity, assertion: (ExternalSystemModuleOptionsEntity) -> Unit) {
    val exModuleOptions = module.exModuleOptions
    Assertions.assertNotNull(module) {
      "Cannot find ExternalSystemModuleOptionsEntity for the '${module.name}' module"
    }
    assertion(exModuleOptions!!)
  }
}