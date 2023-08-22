// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.workspace.jps.JpsGlobalFileEntitySource
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
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
    copyAndLoadGlobalEntities(originalFile = "loading", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { entitySource ->
      val librariesNames = listOf("aws.s3", "org.maven.common", "com.google.plugin", "org.microsoft")
      val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
      libraryTable as GlobalLibraryTableBridgeImpl
      val libraryBridges = libraryTable.libraries
      Assert.assertEquals(librariesNames.size, libraryBridges.size)
      UsefulTestCase.assertSameElements(librariesNames, libraryBridges.map { it.name })

      val workspaceModel = GlobalWorkspaceModel.getInstance()
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
}