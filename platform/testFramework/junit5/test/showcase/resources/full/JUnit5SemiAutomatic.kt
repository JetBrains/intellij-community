// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.full

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.ProjectResource
import com.intellij.testFramework.junit5.resources.ResourceExtensionApi
import com.intellij.testFramework.junit5.resources.providers.module.ModuleName
import com.intellij.testFramework.junit5.resources.providers.module.ModuleParams
import com.intellij.testFramework.junit5.resources.providers.module.ModuleProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Class-level [Module] with custom name
 */
@TestApplication
@ProjectResource
class JUnit5SemiAutomatic {

  companion object {
    private val ID = hashCode().toString()

    @JvmStatic
    @RegisterExtension
    val moduleEx = ResourceExtensionApi.forProvider(ModuleProvider {
      ModuleParams(name = ModuleName("$ID-MyTest"))
    })
  }

  @Test
  fun funProjectModule(module: Module, project: Project) {
    assertEquals(module.project, project)
    assertTrue(module.name.startsWith(ID))
  }
}