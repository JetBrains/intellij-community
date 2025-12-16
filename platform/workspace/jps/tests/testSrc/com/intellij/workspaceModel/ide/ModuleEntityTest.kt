// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.writeAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import com.intellij.platform.workspace.jps.entities.sdkId
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.workspaceModel.ide.legacyBridge.findModuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Path

@TestApplication
class ModuleEntityTest {
  private lateinit var sdk: Sdk
  private val moduleFixture = projectFixture().moduleFixture()

  @BeforeEach
  fun registerSdk(@TempDir tempDir: Path): Unit = timeoutRunBlocking {
    val tempDirVfs = withContext(Dispatchers.IO) { VfsUtil.findFile(tempDir, true)!! }
    sdk = SdkConfigurationUtil.setupSdk(emptyArray<Sdk>(), tempDirVfs, SdkType.EP_NAME.extensionList.first(), true, null, "mySDKname")!!
    writeAction {
      ProjectJdkTable.getInstance().addJdk(sdk)
    }
  }

  @AfterEach
  fun dropSdk(): Unit = timeoutRunBlocking {
    writeAction {
      ProjectJdkTable.getInstance().removeJdk(sdk)
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [true, false])
  fun testModuleSdk(removeExistingSdk: Boolean): Unit = timeoutRunBlocking {
    val module = moduleFixture.get()
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
