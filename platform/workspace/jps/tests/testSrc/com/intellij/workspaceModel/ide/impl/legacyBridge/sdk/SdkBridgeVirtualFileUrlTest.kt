// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.sdk

import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkEntityBuilder
import com.intellij.platform.workspace.jps.entities.SdkRoot
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.storage.InternalEnvironmentName
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.entities
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.createIdeVirtualFileUrlManager
import org.junit.jupiter.api.Test
import kotlin.test.assertSame

/**
 * A [SdkBridgeImpl] always makes new URLs in the global [VirtualFileUrlManager].
 * `SdkModificatorBridgeImpl.addRoot` is an example.
 * But the entity builder of the bridge can come from a project storage.
 * `GlobalSdkBridgeInitializer.initializeBridges` does this, and the URLs then belong to the manager of that project.
 *
 * A manager makes one instance for each URL, and two instances from different managers are never equal.
 * Therefore `applyChangesFrom` must make the URLs again in the global manager.
 * If it does not, `roots` keeps instances from two managers, and these functions do not find the correct entity:
 * `VirtualFileUrlIndex.findEntitiesByUrl`, and the code in `VirtualFileUrlWatcher` that finds a root.
 */
@TestApplication
class SdkBridgeVirtualFileUrlTest {

  @Test
  fun `applyChangesFrom makes the urls of a builder again in the global manager`() {
    val globalManager = globalVirtualFileUrlManager()
    val foreignManager = projectVirtualFileUrlManager()

    val copy = emptySdkEntity()
    copy.applyChangesFrom(sdkEntityBuilder(foreignManager))

    assertUrlsBelongTo(globalManager, copy)
  }

  @Test
  fun `applyChangesFrom makes the urls of an entity again in the global manager`() {
    val globalManager = globalVirtualFileUrlManager()
    val foreignManager = projectVirtualFileUrlManager()

    val storage = MutableEntityStorage.create()
    storage.addEntity(sdkEntityBuilder(foreignManager))
    val foreignEntity = storage.toSnapshot().entities<SdkEntity>().single()

    val copy = emptySdkEntity()
    copy.applyChangesFrom(foreignEntity)

    assertUrlsBelongTo(globalManager, copy)
  }

  private fun globalVirtualFileUrlManager(): VirtualFileUrlManager =
    GlobalWorkspaceModel.getInstance(LocalEelMachine).getVirtualFileUrlManager()

  private fun projectVirtualFileUrlManager(): VirtualFileUrlManager {
    return createIdeVirtualFileUrlManager()
  }

  private fun emptySdkEntity(): SdkEntityBuilder =
    SdkBridgeImpl.createEmptySdkEntity(SDK_NAME, SDK_TYPE, environmentName = InternalEnvironmentName.Local)

  private fun sdkEntityBuilder(manager: VirtualFileUrlManager): SdkEntityBuilder =
    SdkEntity(
      name = SDK_NAME,
      type = SDK_TYPE,
      roots = listOf(SdkRoot(manager.getOrCreateFromUrl(CLASSES_URL), SdkRootTypeId("classPath"))),
      additionalData = "",
      entitySource = SdkBridgeImpl.createEntitySourceForSdk(InternalEnvironmentName.Local),
    ) {
      homePath = manager.getOrCreateFromUrl(HOME_URL)
    }

  private fun assertUrlsBelongTo(manager: VirtualFileUrlManager, sdk: SdkEntityBuilder) {
    assertSame(
      manager.getOrCreateFromUrl(CLASSES_URL), sdk.roots.single().url,
      "the URL of the SDK root must be the instance from the given manager",
    )
    assertSame(
      manager.getOrCreateFromUrl(HOME_URL), sdk.homePath,
      "the home path must be the instance from the given manager",
    )
  }

  private companion object {
    const val SDK_NAME = "test-sdk"
    const val SDK_TYPE = "JavaSDK"
    const val CLASSES_URL = "jar:///jdk/lib/rt.jar!/"
    const val HOME_URL = "file:///jdk"
  }
}
