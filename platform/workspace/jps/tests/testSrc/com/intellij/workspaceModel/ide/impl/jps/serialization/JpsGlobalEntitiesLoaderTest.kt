// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
 import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JpsGlobalEntitiesLoaderTest {
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
  fun `test global libraries loading`() {
    copyAndLoadGlobalEntities(originalFile = "libraries/loading", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { _, _ ->
      val librariesNames = listOf("aws.s3", "org.maven.common", "com.google.plugin", "org.microsoft")
      val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
      libraryTable as GlobalLibraryTableBridgeImpl
      val libraryBridges = libraryTable.libraries
      Assert.assertEquals(librariesNames.size, libraryBridges.size)
      UsefulTestCase.assertSameElements(librariesNames, libraryBridges.map { it.name })

      val workspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
      val libraryEntities = workspaceModel.currentSnapshot.entities(LibraryEntity::class.java).toList()
      Assert.assertEquals(librariesNames.size, libraryEntities.size)
      UsefulTestCase.assertSameElements(librariesNames, libraryEntities.map { it.name })
      libraryEntities.forEach { libraryEntity ->
        Assert.assertEquals(JpsGlobalFileEntitySource::class, libraryEntity.entitySource::class)
        Assert.assertEquals(LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL), libraryEntity.tableId)

        Assert.assertEquals(1, libraryEntity.roots.size)
        val libraryRoot = libraryEntity.roots.single()
        Assert.assertEquals(LibraryRootTypeId.COMPILED, libraryRoot.type)
      }
    }
  }

  @Test
  fun `test sdks loading`() {
    copyAndLoadGlobalEntities(originalFile = "sdk/loading", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { _, _ ->
      data class SdkTestInfo(val name: String, val version: String, val type: String)

      val sdkInfos = listOf(SdkTestInfo("corretto-20", "Amazon Corretto version 20.0.2", "JavaSDK"),
                            SdkTestInfo("jbr-17", "java version \"17.0.7\"", "JavaSDK"))
      val sdkBridges = ProjectJdkTable.getInstance().allJdks
      Assert.assertEquals(sdkInfos.size, sdkBridges.size)
      UsefulTestCase.assertSameElements(sdkInfos, sdkBridges.map { SdkTestInfo(it.name, it.versionString!!, it.sdkType.name) })

      val workspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
      val sdkEntities = workspaceModel.currentSnapshot.entities(SdkEntity::class.java).toList()
      Assert.assertEquals(sdkInfos.size, sdkEntities.size)
      UsefulTestCase.assertSameElements(sdkInfos, sdkEntities.map { SdkTestInfo(it.name, it.version!!, it.type) })
      sdkEntities.forEach { sdkEntity ->
        Assert.assertEquals(JpsGlobalFileEntitySource::class, sdkEntity.entitySource::class)

        val sdkRoots = sdkEntity.roots
        Assert.assertEquals(4, sdkRoots.size)
        Assert.assertEquals(1, sdkRoots.count { it.type.name == "sourcePath" })
        Assert.assertEquals(1, sdkRoots.count { it.type.name == "annotationsPath" })
        Assert.assertEquals(2, sdkRoots.count { it.type.name == "classPath" })
      }
    }
  }
}