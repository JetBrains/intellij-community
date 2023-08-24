// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.UsefulTestCase
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.workspaceModel.ide.getGlobalInstance
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryRoot
import com.intellij.platform.workspace.jps.entities.LibraryRootTypeId
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
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
  fun `test project and global storage sync`() {
    copyAndLoadGlobalEntities(originalFile = "loading", expectedFile = "sync", testDir = temporaryFolder.newFolder(),
                              parentDisposable = disposableRule.disposable) { entitySource ->
      val projectLibrariesNames = mutableListOf("spring", "junit", "kotlin")
      val globalLibrariesNames = mutableListOf("aws.s3", "org.maven.common", "com.google.plugin", "org.microsoft")

      val globalLibraryEntities = GlobalWorkspaceModel.getInstance().currentSnapshot.entities(LibraryEntity::class.java).toList()
      UsefulTestCase.assertSameElements(globalLibrariesNames, globalLibraryEntities.map { it.name })

      val loadedProjects = listOf(loadProject(), loadProject())
      // Check that global storage is the same after projects load
      checkLibrariesInStorages(globalLibrariesNames, projectLibrariesNames, loadedProjects)

      ApplicationManager.getApplication().invokeAndWait {
        runWriteAction {
          GlobalWorkspaceModel.getInstance().updateModel("Test update") { builder ->
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
          val virtualFileManager = VirtualFileUrlManager.getInstance(project)
          WorkspaceModel.getInstance(project).updateProjectModel("Test update") { builder ->
            val libraryEntity = builder.entities(LibraryEntity::class.java).first { it.name == "com.google.plugin" }
            val libraryNameToRemove = libraryEntity.name
            builder.removeEntity(libraryEntity)
            globalLibrariesNames.remove(libraryNameToRemove)

            val gradleLibraryEntity = LibraryEntity("com.gradle",
                                                    LibraryTableId.GlobalLibraryTableId(LibraryTablesRegistrar.APPLICATION_LEVEL),
                                                    listOf(
                                                      LibraryRoot(virtualFileManager.fromUrl("/a/b/one.txt"), LibraryRootTypeId.SOURCES)),
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

    val globalWorkspaceModel = GlobalWorkspaceModel.getInstance()
    val globalVirtualFileUrlManager = VirtualFileUrlManager.getGlobalInstance()

    val globalLibraryEntities = globalWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java).associateBy { it.name }
    UsefulTestCase.assertSameElements(globalLibrariesNames, globalLibraryEntities.keys)

    loadedProjects.forEach { loadedProject ->
      val projectWorkspaceModel = WorkspaceModel.getInstance(loadedProject)
      val projectVirtualFileUrlManager = VirtualFileUrlManager.getInstance(loadedProject)
      projectWorkspaceModel as WorkspaceModelImpl
      val projectLibraryEntities = projectWorkspaceModel.currentSnapshot.entities(LibraryEntity::class.java).toList()
      UsefulTestCase.assertSameElements(projectLibrariesNames + globalLibrariesNames, projectLibraryEntities.map { it.name })

      // Check VirtualFileUrls are from different managers but same url
      projectLibraryEntities.forEach libsLoop@{ projectLibrary ->
        if (!globalLibrariesNames.contains(projectLibrary.name)) return@libsLoop
        val projectVfu = projectLibrary.roots[0].url
        val globalVfu = globalLibraryEntities[projectLibrary.name]!!.roots[0].url

        assertEquals(globalVfu.url, projectVfu.url)
        assertSame(projectVfu, projectVirtualFileUrlManager.fromUrl(projectVfu.url))
        assertSame(globalVfu, globalVirtualFileUrlManager.fromUrl(globalVfu.url))
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
}