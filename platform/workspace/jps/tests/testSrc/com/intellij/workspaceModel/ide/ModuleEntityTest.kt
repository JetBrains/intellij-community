// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.testFramework.junit5.projectStructure.fixture.sdkFixture
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class ModuleEntityTest {
  private val projectFixture = projectFixture()
  private val moduleFixture = projectFixture.moduleFixture()
  private val dirFixture = tempPathFixture()
  private val sdkFixture = projectFixture.sdkFixture("mySDKName", SdkType.EP_NAME.extensionList.first(), dirFixture)


  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun testModuleSdk(removeExistingSdk: Boolean): Unit = timeoutRunBlocking {
    val module = moduleFixture.get()
    val sdk = sdkFixture.get()
    val (sdkIdToSet, expectedSdk) = if (removeExistingSdk) Pair(null, null) else Pair(SdkId(sdk.name, sdk.sdkType.name), sdk)

    module.project.workspaceModel.update("...") { storage ->
      storage.modifyModuleEntity(module.findModuleEntity(storage)!!) {
        repeat(10) { // Multiple calls should be safe
          sdkId = sdkIdToSet
        }
      }
    }
    Assertions.assertEquals(expectedSdk,
                            ModuleRootManager.getInstance(module).sdk,
                            "Sdk should ${if (removeExistingSdk) "not" else ""} be set")
  }
}
