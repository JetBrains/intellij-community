// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.ProjectModelRule
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

class ModuleManagerBridgeTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val projectModel = ProjectModelRule()

  @Test
  fun `remove module with module library`() {
    val module = projectModel.createModule()
    projectModel.addModuleLevelLibrary(module, "foo")
    assertThat(storage.entities(ModuleEntity::class.java).toList()).hasSize(1)
    assertThat(storage.entities(LibraryEntity::class.java).toList()).hasSize(1)
    projectModel.removeModule(module)
    assertThat(storage.entities(ModuleEntity::class.java).toList()).isEmpty()
    assertThat(storage.entities(LibraryEntity::class.java).toList()).isEmpty()
  }

  private val storage
    get() = WorkspaceModel.getInstance(projectModel.project).entityStorage.current
}