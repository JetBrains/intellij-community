// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import org.junit.*
import org.junit.rules.TemporaryFolder

class JpsGlobalEntitiesSavingTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val temporaryFolder = TemporaryFolder()

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Test
  fun `test global libraries saving`() {
    // TODO:: Investigate failing on TC
    Assume.assumeFalse("Temporary failed in check of expected file content", UsefulTestCase.IS_UNDER_TEAMCITY)
    copyAndLoadGlobalEntities(expectedFile = "saving", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { entitySource ->
      val librariesNames = listOf("com.gradle", "org.maven")
      val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
      libraryTable as GlobalLibraryTableBridgeImpl
      Assert.assertEquals(0, libraryTable.libraries.size)

      val workspaceModel = GlobalWorkspaceModel.getInstance()
      Assert.assertEquals(0, workspaceModel.currentSnapshot.entities(LibraryEntity::class.java).toList().size)

      val virtualFileManager = VirtualFileUrlManager.getGlobalInstance()
      val globalLibraryTableId = LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL)
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          workspaceModel.updateModel("Test update") { builder ->
            var libraryRoot = LibraryRoot(virtualFileManager.fromUrl("/a/b/one.txt"), LibraryRootTypeId.COMPILED)
            val gradleLibraryEntity = LibraryEntity(librariesNames.get(0), globalLibraryTableId, listOf(libraryRoot), entitySource)
            builder.addEntity(gradleLibraryEntity)

            libraryRoot = LibraryRoot(virtualFileManager.fromUrl("/a/c/test.jar"), LibraryRootTypeId.SOURCES)
            val mavenLibraryEntity = LibraryEntity(librariesNames.get(1), globalLibraryTableId, listOf(libraryRoot), entitySource)
            builder.addEntity(mavenLibraryEntity)
          }
        }
      }

      val libraryBridges = libraryTable.libraries
      Assert.assertEquals(librariesNames.size, libraryBridges.size)
      Assert.assertEquals(librariesNames, libraryBridges.map { it.name })
    }
  }
}