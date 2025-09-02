// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.projectRoots.impl.UnknownSdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@TestApplication
internal class MultiverseFixtureTest {
  companion object {
    val projectFixture = multiverseProjectFixture {
      module("a") {
        contentRoot("root") {
          sourceRoot("src", "shared") {
            file("A.java", "class A {}")
          }
        }
      }
      module("b") {
        sharedSourceRoot("shared")
      }
    }
  }

  @Test
  fun `assert project structure`() = timeoutRunBlocking {
    readAction {
      val project = projectFixture.get()
      val moduleManager = ModuleManager.getInstance(project)
      val modules = moduleManager.modules
      val sourceRoots = modules.map { module ->
        module.rootManager.sourceRoots.single()
      }.distinct()

      assertEquals(1, sourceRoots.size) { sourceRoots.toString() }
    }
  }
}

@TestApplication
internal class SdkMultiverseFixtureTest {
  companion object {
    val projectFixture = multiverseProjectFixture {
      sdk("sdk", UnknownSdkType.getInstance("my-sdk")) {}
      module("a") {
        dependencies {
          useSdk("sdk")
        }
      }
    }
  }

  @Test
  fun `assert project structure`() = timeoutRunBlocking {
    readAction {
      val project = projectFixture.get()
      val moduleManager = ModuleManager.getInstance(project)
      val modules = moduleManager.modules
      val module = modules[0]
      val sdk = requireNotNull(ModuleRootManager.getInstance(module).sdk)
      assertEquals("sdk", sdk.name)
    }
  }
}


