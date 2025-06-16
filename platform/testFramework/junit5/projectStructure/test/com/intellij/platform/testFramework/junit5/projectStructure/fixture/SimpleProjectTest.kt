// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.projectStructure.fixture

import com.intellij.openapi.application.readAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.rootManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@TestApplication
internal class SimpleProjectTest {
  val projectFixture = multiverseProjectFixture {
    module("a") {
      contentRoot("root") {
        sourceRoot("src", "shared") {
          file("A.java", "class A {}")
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
      assertEquals(1, modules.size, modules.toString())

      val sourceRoots = modules.single().rootManager.sourceRoots
      assertEquals(1, sourceRoots.size, sourceRoots.toString())

      val sourceRoot = sourceRoots.single()
      val path = sourceRoot.path
      assert(path.endsWith("a/root/src")) { path }
    }
  }
}