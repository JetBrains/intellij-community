// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.eel.provider.LocalEelDescriptor
import com.intellij.platform.eel.provider.LocalEelMachine
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class JpsGlobalEntitiesSyncTest {
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
  fun `test project and global storage sdk sync`() {
    copyAndLoadGlobalEntities(originalFile = "sdk/loading", expectedFile = "sdk/sync", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable, ) { _, entitySource ->
      val sdkInfos = mutableListOf(SdkTestInfo("corretto-20", "Amazon Corretto version 20.0.2", "JavaSDK"),
                            SdkTestInfo("jbr-17", "java version \"17.0.7\"", "JavaSDK"))
      val sdkEntities = GlobalWorkspaceModel.getInstance(LocalEelMachine).currentSnapshot.entities(SdkEntity::class.java).toList()
      UsefulTestCase.assertSameElements(sdkInfos, sdkEntities.map { SdkTestInfo(it.name, it.version!!, it.type) })

      val loadedProjects = listOf(loadProject(), loadProject())
      // Check that global storage is the same after projects load
      checkSdkInStorages(sdkInfos, loadedProjects)

      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          GlobalWorkspaceModel.getInstance(LocalEelMachine).updateModel("Test update") { builder ->
            val sdkEntity = builder.entities(SdkEntity::class.java).first { it.name == "corretto-20" }
            val sdkNameToRemove = sdkEntity.name
            builder.removeEntity(sdkEntity)
            sdkInfos.removeIf { it.name == sdkNameToRemove }
          }
        }
      }
      // Check global entities in sync at project storage
      checkSdkInStorages(sdkInfos, loadedProjects)

      // Check global entities in sync after removing entity via project storage
      val project = loadedProjects.first()
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          WorkspaceModel.getInstance(project).updateProjectModel("Test update") { builder ->
            val sdkEntity = builder.entities(SdkEntity::class.java).first { it.name == "jbr-17" }
            val sdkNameToRemove = sdkEntity.name
            builder.removeEntity(sdkEntity)
            sdkInfos.removeIf { it.name == sdkNameToRemove }
          }
        }
      }
      checkSdkInStorages(sdkInfos, loadedProjects)

      // Check global entities in sync after adding entity via project storage
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          val virtualFileManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
          WorkspaceModel.getInstance(project).updateProjectModel("Test update") { builder ->
            val projectSdkEntity = SdkEntity("oracle-1.8", "JavaSDK",
                                             listOf(SdkRoot(virtualFileManager.getOrCreateFromUrl("/Library/Java/JavaVirtualMachines/oracle-1.8/Contents/Home!/java.base"), SdkRootTypeId("sourcePath"))),
                                             "", entitySource) {
              homePath = virtualFileManager.getOrCreateFromUrl("/Library/Java/JavaVirtualMachines/oracle-1.8/Contents/Home")
              version = "1.8"
            }
            builder.addEntity(projectSdkEntity)
            sdkInfos.add(SdkTestInfo(projectSdkEntity.name, projectSdkEntity.version!!, projectSdkEntity.type))
          }
        }
      }
      checkSdkInStorages(sdkInfos, loadedProjects)
      ApplicationManager.getApplication().invokeAndWait {
        (ProjectManager.getInstance() as ProjectManagerEx).closeAndDisposeAllProjects(false)
      }
    }
  }

  private fun checkSdkInStorages(sdkInfos: List<SdkTestInfo>, loadedProjects: List<Project>) {
    val sdkBridges = ProjectJdkTable.getInstance().allJdks
    UsefulTestCase.assertSameElements(sdkBridges.map { SdkTestInfo(it.name, it.versionString!!, it.sdkType.name) }, sdkInfos)

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
    val globalVirtualFileUrlManager = globalWorkspaceModel.getVirtualFileUrlManager()

    val sdkEntities = globalWorkspaceModel.currentSnapshot.entities(SdkEntity::class.java).toList()
    UsefulTestCase.assertSameElements(sdkInfos, sdkEntities.map { SdkTestInfo(it.name, it.version!!, it.type) })

    loadedProjects.forEach { loadedProject ->
      val projectWorkspaceModel = WorkspaceModel.getInstance(loadedProject)
      val projectVirtualFileUrlManager = projectWorkspaceModel.getVirtualFileUrlManager()
      projectWorkspaceModel as WorkspaceModelImpl
      val projectSdkEntities = projectWorkspaceModel.currentSnapshot.entities(SdkEntity::class.java).toList()
      UsefulTestCase.assertSameElements(sdkInfos, projectSdkEntities.map { SdkTestInfo(it.name, it.version!!, it.type) })

      // Check VirtualFileUrls are from different managers but same url
      projectSdkEntities.forEach libsLoop@{ projectSdk ->
        val projectVfu = projectSdk.roots[0].url
        val globalVfu = sdkEntities.find { it.name == projectSdk.name }!!.roots[0].url

        assertEquals(globalVfu.url, projectVfu.url)
        assertSame(projectVfu, projectVirtualFileUrlManager.getOrCreateFromUrl(projectVfu.url))
        assertSame(globalVfu, globalVirtualFileUrlManager.getOrCreateFromUrl(globalVfu.url))
        assertNotSame(globalVfu, projectVfu)
      }
    }
  }

  @Test
  fun `test project and global storage library sync`() {
    copyAndLoadGlobalEntities(originalFile = "libraries/loading", expectedFile = "libraries/sync", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { entitySource, _ ->
      val projectLibrariesNames = mutableListOf("spring", "junit", "kotlin")
      val globalLibrariesNames = mutableListOf("aws.s3", "org.maven.common", "com.google.plugin", "org.microsoft")

      val globalLibraryEntities = GlobalWorkspaceModel.getInstance(LocalEelMachine).currentSnapshot.entities(LibraryEntity::class.java).toList()
      UsefulTestCase.assertSameElements(globalLibrariesNames, globalLibraryEntities.map { it.name })

      val loadedProjects = listOf(loadProject(), loadProject())
      // Check that global storage is the same after projects load
      checkLibrariesInStorages(globalLibrariesNames, projectLibrariesNames, loadedProjects)

      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          GlobalWorkspaceModel.getInstance(LocalEelMachine).updateModel("Test update") { builder ->
            val libraryEntity = builder.entities(LibraryEntity::class.java).first{ it.name == "aws.s3" }
            val libraryNameToRemove = libraryEntity.name
            builder.removeEntity(libraryEntity)
            globalLibrariesNames.remove(libraryNameToRemove)
          }
        }
      }
      // Check global entities in sync at project storage
      checkLibrariesInStorages(globalLibrariesNames, projectLibrariesNames, loadedProjects)

      // Check global entities in sync after project storage update
      val project = loadedProjects.first()
      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          val workspaceModel = WorkspaceModel.getInstance(project)
          val virtualFileManager = workspaceModel.getVirtualFileUrlManager()
          workspaceModel.updateProjectModel("Test update") { builder ->
            val libraryEntity = builder.entities(LibraryEntity::class.java).first { it.name == "com.google.plugin" }
            val libraryNameToRemove = libraryEntity.name
            builder.removeEntity(libraryEntity)
            globalLibrariesNames.remove(libraryNameToRemove)

            val gradleLibraryEntity = LibraryEntity("com.gradle",
                                                    LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL),
                                                    listOf(
                                                      LibraryRoot(virtualFileManager.getOrCreateFromUrl("/a/b/one.txt"), LibraryRootTypeId.SOURCES)),
                                                    entitySource)
            builder.addEntity(gradleLibraryEntity)
            globalLibrariesNames.add(gradleLibraryEntity.name)
          }
        }
      }
      checkLibrariesInStorages(globalLibrariesNames, projectLibrariesNames, loadedProjects)
      ApplicationManager.getApplication().invokeAndWait {
        (ProjectManager.getInstance() as ProjectManagerEx).closeAndDisposeAllProjects(false)
      }
    }
  }

  private fun checkLibrariesInStorages(globalLibrariesNames: List<String>,
                                       projectLibrariesNames: List<String>,
                                       loadedProjects: List<Project>) {
    val libraryTable = LibraryTablesRegistrar.getInstance().libraryTable
    libraryTable as GlobalLibraryTableBridgeImpl
    val libraryBridges = libraryTable.libraries
    UsefulTestCase.assertSameElements(globalLibrariesNames, libraryBridges.map { it.name })

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance(LocalEelMachine)
    val globalVirtualFileUrlManager = globalWorkspaceModel.getVirtualFileUrlManager()

    val globalLibraryEntities = globalWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java).associateBy { it.name }
    UsefulTestCase.assertSameElements(globalLibrariesNames, globalLibraryEntities.keys)

    loadedProjects.forEach { loadedProject ->
      val projectWorkspaceModel = WorkspaceModel.getInstance(loadedProject)
      val projectVirtualFileUrlManager = projectWorkspaceModel.getVirtualFileUrlManager()
      projectWorkspaceModel as WorkspaceModelImpl
      val projectLibraryEntities = projectWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java).toList()
      UsefulTestCase.assertSameElements(projectLibrariesNames + globalLibrariesNames, projectLibraryEntities.map { it.name })

      // Check VirtualFileUrls are from different managers but same url
      projectLibraryEntities.forEach libsLoop@{ projectLibrary ->
        if (!globalLibrariesNames.contains(projectLibrary.name)) return@libsLoop
        val projectVfu = projectLibrary.roots[0].url
        val globalVfu = globalLibraryEntities[projectLibrary.name]!!.roots[0].url

        assertEquals(globalVfu.url, projectVfu.url)
        assertSame(projectVfu, projectVirtualFileUrlManager.getOrCreateFromUrl(projectVfu.url))
        assertSame(globalVfu, globalVirtualFileUrlManager.getOrCreateFromUrl(globalVfu.url))
        assertNotSame(globalVfu, projectVfu)
      }
    }
  }

  private fun loadProject(): Project {
    val tmpFolder = temporaryFolder.newFolder()
    val projectDir = File(PathManagerEx.getCommunityHomePath(),
                          "platform/workspace/jps/tests/testData/serialization/moduleTestProperties")
    FileUtil.copyDir(projectDir, tmpFolder)
    return PlatformTestUtil.loadAndOpenProject(tmpFolder.toPath(), disposableRule.disposable)
  }

  data class SdkTestInfo(val name: String, val version: String, val type: String)
}