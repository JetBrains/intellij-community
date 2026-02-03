// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.rootManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertContains
import kotlin.test.assertEquals

@TestApplication
internal class ProjectWithDependenciesTest {
  val projectFixture = multiverseProjectFixture {
    module("a") {
      contentRoot("root") {
        sourceRoot("src", "id") {
          file("A.java", "class A {}")
        }
      }
    }
    module("b") {
      dependencies {
        module("a")
      }
      contentRoot("root") {
        sourceRoot("src", "id2") {
          file("B.java", "class B {}")
        }
      }
    }
    module("c") {
      dependencies {
        module("a")
        module("b")
      }
    }
  }

  @Test
  fun `assert project dependencies`() = timeoutRunBlocking {
    readAction {
      val project = projectFixture.get()
      val moduleManager = ModuleManager.getInstance(project)
      val modules = moduleManager.modules
      assertEquals(3, modules.size, modules.toString())

      val moduleA = moduleManager.findModuleByName("a")
      assertNotNull(moduleA, "Module A is not found")

      val dependenciesA = moduleA.rootManager.dependencies
      assertEquals(0, dependenciesA.size, modules.toString())

      val moduleB = moduleManager.findModuleByName("b")
      assertNotNull(moduleB, "Module B is not found")

      val dependenciesB = moduleB.rootManager.dependencies
      assertEquals(1, dependenciesB.size, dependenciesB.toString())
      val dependencyB = dependenciesB.single()
      assertEquals(moduleA, dependencyB)

      val moduleC = moduleManager.findModuleByName("c")
      assertNotNull(moduleC, "Module C is not found")

      val dependenciesC = moduleC.rootManager.dependencies
      assertEquals(2, dependenciesC.size, modules.toString())
      assertContains(dependenciesC, moduleA)
      assertContains(dependenciesC, moduleB)
    }
  }
}