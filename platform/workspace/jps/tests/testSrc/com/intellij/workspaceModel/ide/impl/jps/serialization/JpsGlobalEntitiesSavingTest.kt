// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.AnnotationOrderRootType
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.PersistentOrderRootType
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
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
    copyAndLoadGlobalEntities(expectedFile = "libraries/saving", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { entitySource, _ ->
      val librariesNames = listOf("com.gradle", "org.maven")
      val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
      libraryTable as GlobalLibraryTableBridgeImpl
      Assert.assertEquals(0, libraryTable.libraries.size)

      val workspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
      Assert.assertEquals(0, workspaceModel.currentSnapshot.entities(LibraryEntity::class.java).toList().size)

      val virtualFileManager = workspaceModel.getVirtualFileUrlManager()
      val globalLibraryTableId = LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL)
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          workspaceModel.updateModel("Test update") { builder ->
            var libraryRoot = LibraryRoot(virtualFileManager.getOrCreateFromUrl("/a/b/one.txt"), LibraryRootTypeId.COMPILED)
            val gradleLibraryEntity = LibraryEntity(librariesNames.get(0), globalLibraryTableId, listOf(libraryRoot), entitySource)
            builder.addEntity(gradleLibraryEntity)

            libraryRoot = LibraryRoot(virtualFileManager.getOrCreateFromUrl("/a/c/test.jar"), LibraryRootTypeId.SOURCES)
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

  @Test
  fun `test global sdk saving`() {
    // TODO:: Investigate failing on TC
    Assume.assumeFalse("Temporary failed in check of expected file content", UsefulTestCase.IS_UNDER_TEAMCITY)
    copyAndLoadGlobalEntities(expectedFile = "sdk/saving", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { entitySource, _ ->
      OrderRootType.EP_NAME.point.registerExtension(AnnotationOrderRootType(), disposableRule.disposable)
      val sdkNames = listOf("jbr-2048", "amazon.crevetto")
      val sdks = ProjectJdkTable.getInstance().allJdks
      Assert.assertEquals(0, sdks.size)

      val workspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
      Assert.assertEquals(0, workspaceModel.currentSnapshot.entities(SdkEntity::class.java).toList().size)

      val virtualFileManager = workspaceModel.getVirtualFileUrlManager()
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          workspaceModel.updateModel("Test update") { builder ->
            var sdkRoots = listOf(SdkRoot(virtualFileManager.getOrCreateFromUrl("/Contents/Home!/java.compiler"), SdkRootTypeId(OrderRootType.CLASSES.customName)),
                                  SdkRoot(virtualFileManager.getOrCreateFromUrl("/lib/src.zip!/java.se"), SdkRootTypeId(OrderRootType.SOURCES.customName)))
            val jbrSdkEntity = SdkEntity(sdkNames[0], "JavaSDK", sdkRoots, "", entitySource) {
              this.homePath = virtualFileManager.getOrCreateFromUrl("/Library/Java/JavaVirtualMachines/jbr-2048/Contents/Home")
            }
            builder.addEntity(jbrSdkEntity)

            sdkRoots = listOf(SdkRoot(virtualFileManager.getOrCreateFromUrl("/Contents/plugins/java/lib/resources/jdkAnnotations.jar"), SdkRootTypeId(AnnotationOrderRootType.getInstance().customName)))
            val amazonSdkEntity = SdkEntity(sdkNames[1], "JavaSDK", sdkRoots, "", entitySource) {
              this.homePath = virtualFileManager.getOrCreateFromUrl("/Library/Java/JavaVirtualMachines/amazon.crevetto/Contents/Home")
            }
            builder.addEntity(amazonSdkEntity)
          }
        }
      }

      val sdkBridges = ProjectJdkTable.getInstance().allJdks
      Assert.assertEquals(sdkNames.size, sdkBridges.size)
      Assert.assertEquals(sdkNames, sdkBridges.map { it.name })
    }
  }

  private val OrderRootType.customName: String
    get() {
      if (this is PersistentOrderRootType) {
        // Only `NativeLibraryOrderRootType` don't have rootName all other elements with it
        return sdkRootName ?: name()
      }
      else {
        // It's only for `DocumentationRootType` this is the only class that doesn't extend `PersistentOrderRootType`
        return name()
      }
    }
}