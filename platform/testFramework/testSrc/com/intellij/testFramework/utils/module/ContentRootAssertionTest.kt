// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.assertion.moduleAssertion.ContentRootAssertions.assertContentRoots
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class ContentRootAssertionTest : ModuleAssertionTestCase() {

  @Test
  fun `test ContentRootAssertions#assertContentRoots`() {
    runBlocking {
      project.workspaceModel.update {
        addModuleEntity("project", ".")
        addModuleEntity("project.module1", "module1")
        addModuleEntity("project.module2", "module2")
      }
      assertContentRoots(project, "project", projectRoot)
      assertContentRoots(project, "project.module1", projectRoot.resolve("module1"))
      assertContentRoots(project, "project.module2", projectRoot.resolve("module2"))
      Assertions.assertThrows(AssertionError::class.java) {
        assertContentRoots(project, "module1", projectRoot.resolve("module1"))
      }
      Assertions.assertThrows(AssertionError::class.java) {
        assertContentRoots(project, "project", projectRoot, projectRoot.resolve("other-root"))
      }
      Assertions.assertThrows(AssertionError::class.java) {
        assertContentRoots(project, "other-module", projectRoot.resolve("other-module"))
      }
    }
  }
}