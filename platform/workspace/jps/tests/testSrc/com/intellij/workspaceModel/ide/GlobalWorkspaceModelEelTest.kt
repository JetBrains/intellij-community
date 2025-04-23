// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.platform.eel.EelPlatform
import com.intellij.platform.eel.path.EelPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.testFramework.junit5.eel.fixture.eelFixture
import com.intellij.platform.testFramework.junit5.eel.fixture.tempDirFixture
import com.intellij.platform.workspace.storage.testEntities.entities.SampleEntitySource
import com.intellij.platform.workspace.storage.testEntities.entities.StringEntity
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.application
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModelRegistry
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test

@TestApplication
class GlobalWorkspaceModelEelTest {
  val eel = eelFixture(EelPlatform.Linux(EelPlatform.Arch.Unknown))

  val eelTempDir = eel.tempDirFixture()
  val eelProject = projectFixture(eelTempDir)

  val localProject = projectFixture()

  fun assumeRegistryValueSet() {
    /**
     * Cannot use [com.intellij.testFramework.junit5.RegistryKey] here because here we need to have registry value
     * even before [com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache] is initialized
     * (see [com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModelSeparationListener])
     */
    Assumptions.assumeTrue(Registry.get("ide.workspace.model.per.environment.model.separation").asBoolean())
  }

  @Test
  fun `global workspace model entities are separated`(): Unit = timeoutRunBlocking {
    assumeRegistryValueSet()
    application.service<GlobalWorkspaceModelRegistry>().dropCaches()

    val globalWorkspaceModelEel = GlobalWorkspaceModel.getInstance(eelProject.get().getEelDescriptor())

    edtWriteAction {
      globalWorkspaceModelEel.updateModel("A test update") { mutableStorage ->
        mutableStorage.addEntity(StringEntity("sample", SampleEntitySource("test eel")))
      }
    }
    val eelEntities = GlobalWorkspaceModel.getInstance(eelProject.get().getEelDescriptor()).currentSnapshot.entities(StringEntity::class.java).toList()
    Assertions.assertFalse(eelEntities.isEmpty())

    val localEntities = GlobalWorkspaceModel.getInstance(localProject.get().getEelDescriptor()).currentSnapshot.entities(StringEntity::class.java).toList()
    Assertions.assertTrue(localEntities.isEmpty())
  }

  @Test
  @SystemProperty("ide.tests.permit.global.workspace.model.serialization", "true")
  fun `global workspace model entities are serialized separately`(): Unit = timeoutRunBlocking {
    assumeRegistryValueSet()
    application.service<GlobalWorkspaceModelRegistry>().dropCaches()

    val globalWorkspaceModelEel = GlobalWorkspaceModel.getInstance(eelProject.get().getEelDescriptor())
    val globalWorkspaceModelLocal = GlobalWorkspaceModel.getInstance(localProject.get().getEelDescriptor())


    edtWriteAction {
      globalWorkspaceModelEel.updateModel("A test update") { mutableStorage ->
        mutableStorage.addEntity(StringEntity("eel sample", SampleEntitySource("test eel")))
      }
      globalWorkspaceModelLocal.updateModel("A test update 2") { mutableStorage ->
        mutableStorage.addEntity(StringEntity("local sample", SampleEntitySource("test local")))
      }
    }

    application.service<GlobalWorkspaceModelCache>().saveCacheNow()
    application.service<GlobalWorkspaceModelRegistry>().dropCaches()

    val eelModel = GlobalWorkspaceModel.getInstance(eelProject.get().getEelDescriptor())
    Assertions.assertTrue(eelModel.loadedFromCache)
    val eelEntities = eelModel.currentSnapshot.entities(StringEntity::class.java).toList()
    Assertions.assertTrue(eelEntities.find { it.data == "eel sample" } != null)
    Assertions.assertFalse(eelEntities.find { it.data == "local sample" } != null)

    val localModel = GlobalWorkspaceModel.getInstance(localProject.get().getEelDescriptor())
    Assertions.assertTrue(localModel.loadedFromCache)
    val localEntities = GlobalWorkspaceModel.getInstance(localProject.get().getEelDescriptor()).currentSnapshot.entities(StringEntity::class.java).toList()
    Assertions.assertTrue(localEntities.find { it.data == "local sample" } != null)
    Assertions.assertFalse(localEntities.find { it.data == "eel sample" } != null)
  }

}