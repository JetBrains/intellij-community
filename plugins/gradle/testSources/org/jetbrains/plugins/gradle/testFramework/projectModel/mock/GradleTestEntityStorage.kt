// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.testFramework.projectModel.mock

import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.workspace.jps.entities.ExternalSystemModuleOptionsEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.exModuleOptions
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.workspaceModel.ide.NonPersistentEntitySource
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path

class GradleTestEntityStorage private constructor() {

  var projectPath: Path = Path.of("project")
  var numHolderModules: Int = 1
  var numSourceSetModules: Int = 1

  companion object {

    val entitySource: EntitySource = NonPersistentEntitySource

    private fun createEntityStorage(configuration: GradleTestEntityStorage): ImmutableEntityStorage {
      val storage = MutableEntityStorage.create()
      repeat(configuration.numHolderModules) { holderModuleIndex ->
        val holderModuleName = GradleTestModuleNames.holderModuleName(holderModuleIndex)
        val holderModulePath = configuration.projectPath.resolve(holderModuleName)
        storage addEntity ModuleEntity(holderModuleName, emptyList(), entitySource) {
          exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
            externalSystem = GradleConstants.SYSTEM_ID.id
            linkedProjectId = holderModuleName
            linkedProjectPath = holderModulePath.toCanonicalPath()
            rootProjectPath = configuration.projectPath.toCanonicalPath()

            externalSystemModuleGroup = null
            externalSystemModuleVersion = null
            externalSystemModuleType = null
          }
        }
        repeat(configuration.numSourceSetModules) { sourceSetModuleIndex ->
          val sourceSetModuleName = GradleTestModuleNames.sourceSetModuleName(holderModuleIndex, sourceSetModuleIndex)
          val sourceSetModuleId = GradleTestModuleNames.sourceSetModuleId(holderModuleIndex, sourceSetModuleIndex)
          storage addEntity ModuleEntity(sourceSetModuleName, emptyList(), entitySource) {
            exModuleOptions = ExternalSystemModuleOptionsEntity(entitySource) {
              externalSystem = GradleConstants.SYSTEM_ID.id
              linkedProjectId = sourceSetModuleId
              linkedProjectPath = holderModulePath.toCanonicalPath()
              rootProjectPath = configuration.projectPath.toCanonicalPath()

              externalSystemModuleGroup = null
              externalSystemModuleVersion = null
              externalSystemModuleType = GradleConstants.GRADLE_SOURCE_SET_MODULE_TYPE_KEY
            }
          }
        }
      }
      return storage.toSnapshot()
    }

    fun testEntityStorage(configure: (GradleTestEntityStorage) -> Unit): ImmutableEntityStorage {
      val configuration = GradleTestEntityStorage()
      configure(configuration)
      return createEntityStorage(configuration)
    }
  }
}
