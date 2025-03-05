// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.module

import com.intellij.platform.testFramework.assertion.moduleAssertion.SourceRootAssertions.assertSourceRoots
import com.intellij.platform.workspace.jps.entities.SourceRootTypeId
import com.intellij.testFramework.useProjectAsync
import com.intellij.testFramework.utils.io.createDirectory
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SourceRootAssertionTest : ModuleAssertionTestCase() {

  @Test
  fun `test SourceRootAssertions#assertModules`() {
    runBlocking {
      val type1 = SourceRootTypeId("type1")
      val type2 = SourceRootTypeId("type2")
      val projectRoot = testDirectory.createDirectory("project")
      generateProject(projectRoot) {
        addModuleEntity("project", ".") {
          addSourceRoot(type1, "source1-1")
          addSourceRoot(type1, "source1-2")
          addSourceRoot(type2, "source2-1")
          addSourceRoot(type2, "source2-2")
        }
        addModuleEntity("project.module1", "module1") {
          addSourceRoot(type1, "module1/source1")
        }
        addModuleEntity("project.module2", "module2") {
          addSourceRoot(type2, "module2/source2")
        }
      }.useProjectAsync { project ->
        assertSourceRoots(project, "project", { it.rootTypeId == type1 }, projectRoot.resolve("source1-1"), projectRoot.resolve("source1-2"))
        assertSourceRoots(project, "project", { it.rootTypeId == type2 }, projectRoot.resolve("source2-1"), projectRoot.resolve("source2-2"))

        assertSourceRoots(project, "project.module1", { it.rootTypeId == type1 }, projectRoot.resolve("module1/source1"))
        assertSourceRoots(project, "project.module1", { it.rootTypeId == type2 })

        assertSourceRoots(project, "project.module2", { it.rootTypeId == type1 })
        assertSourceRoots(project, "project.module2", { it.rootTypeId == type2 }, projectRoot.resolve("module2/source2"))

        Assertions.assertThrows(AssertionError::class.java) {
          assertSourceRoots(project, "module1", { it.rootTypeId == type1 }, projectRoot.resolve("module1/source1"))
        }
        Assertions.assertThrows(AssertionError::class.java) {
          assertSourceRoots(project, "project", { it.rootTypeId == type1 }, projectRoot.resolve("other-root"))
        }
        Assertions.assertThrows(AssertionError::class.java) {
          assertSourceRoots(project, "other-module", { it.rootTypeId == type1 }, projectRoot.resolve("other-module"))
        }
      }
    }
  }
}