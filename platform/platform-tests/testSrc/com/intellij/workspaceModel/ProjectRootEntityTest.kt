// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectRootPersistentStateComponent
import com.intellij.openapi.project.impl.ProjectRootsSynchronizer
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.impl.url.toVirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.project.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.VfsTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.useProjectAsync
import com.intellij.workspaceModel.ide.ProjectRootEntity
import com.intellij.workspaceModel.ide.registerProjectRoot
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.ClassRule
import org.junit.Test
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name
import kotlin.time.Duration.Companion.milliseconds

class ProjectRootEntityTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule: ApplicationRule = ApplicationRule()
  }

  @Test
  fun `test project root entity is updated when file is renamed`(): Unit = runBlocking {
    val name = "new project"
    val projectFile = TemporaryDirectory.generateTemporaryPath(name).createDirectories()
    val renamedProjectFile = projectFile.resolveSibling("${projectFile.fileName}-renamed")
    val options = createTestOpenProjectOptions().copy(projectName = name)

    ProjectUtil.openOrImportAsync(projectFile, options)!!.useProjectAsync { project ->
      val projectRoot = VfsUtil.findFile(projectFile, true)!!
      runWriteActionAndWait {
        projectRoot.rename(this@ProjectRootEntityTest, renamedProjectFile.name)
      }
      VfsTestUtil.syncRefresh()

      val updatedRoots = project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()
      assertThat(updatedRoots.map { it.root }).containsExactly(renamedProjectFile.toVirtualFileUrl(project))
    }
  }

  @Test
  fun `test correct root is added if it contains space symbol`(): Unit = runBlocking {
    val name = "new project"
    val projectFile = TemporaryDirectory.generateTemporaryPath(name).createDirectories()
    val options = createTestOpenProjectOptions().copy(projectName = name)
    ProjectUtil.openOrImportAsync(projectFile, options)!!.useProjectAsync { project ->
      ProjectRootsSynchronizer.doRegister(project) // background startup activities are not executed in unit tests on project open
      val roots = project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()
      assertThat(roots.map { it.root }).containsExactly(projectFile.toVirtualFileUrl(project))
    }
  }

  @Test
  fun `test new ProjectRootEntity is added to ProjectRootPersistentStateComponent by ProjectRootsSynchronizer`(): Unit = runBlocking {
    val name = "project root entity sync"
    val projectFile = TemporaryDirectory.generateTemporaryPath(name).createDirectories()
    val options = createTestOpenProjectOptions().copy(projectName = name)

    ProjectUtil.openOrImportAsync(projectFile, options)!!.useProjectAsync { project ->
      val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
      val additionalRootVfu = projectFile.resolve("additional root").createDirectories().toVirtualFileUrl(project)

      coroutineScope {
        val synchronizerJob = launch { ProjectRootsSynchronizer().execute(project) }
        delay(200.milliseconds) // wait for listener to start
        registerProjectRoot(project, additionalRootVfu)
        waitUntil { additionalRootVfu.url in component.projectRootUrls }
        synchronizerJob.cancelAndJoin()
      }
    }
  }


  @Test
  fun `test ProjectRootEntity is restored by ProjectRootsSynchronizer on reopen from ProjectRootPersistentStateComponent`(): Unit =
    runBlocking {
      val name = "project root entity restore"
      val projectFile = TemporaryDirectory.generateTemporaryPath(name).createDirectories()
      val additionalRoot = projectFile.resolve("additional root").createDirectories()
      val options = createTestOpenProjectOptions().copy(projectName = name)

      ProjectUtil.openOrImportAsync(projectFile, options)!!.useProjectAsync { project ->
        val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
        val rootUrl = projectFile.toVirtualFileUrl(project).url
        registerProjectRoot(project, projectFile)
        registerProjectRoot(project, additionalRoot)
        val additionalRootUrl = additionalRoot.toVirtualFileUrl(project).url
        coroutineScope {
          val synchronizerJob = launch { ProjectRootsSynchronizer().execute(project) }
          waitUntil { rootUrl in component.projectRootUrls && additionalRootUrl in component.projectRootUrls }
          synchronizerJob.cancelAndJoin()
        }

        project.stateStore.save(forceSavingAllSettings = true)
        assertThat(component.projectRootUrls).contains(rootUrl)
      }

      ProjectUtil.openOrImportAsync(projectFile, options)!!.useProjectAsync { project ->
        val component = project.serviceAsync<ProjectRootPersistentStateComponent>()
        val rootVfu = projectFile.toVirtualFileUrl(project)
        val additionalRootVfu = additionalRoot.toVirtualFileUrl(project)
        assertThat(component.projectRootUrls).contains(rootVfu.url)

        project.workspaceModel.update("Drop project root entities to emulate lost workspace model cache") { storage ->
          storage.entities(ProjectRootEntity::class.java).toList().forEach(storage::removeEntity)
        }

        assertThat(project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()).isEmpty()

        ProjectRootsSynchronizer.doRegister(project)

        val restoredRoots = project.workspaceModel.currentSnapshot.entities(ProjectRootEntity::class.java).toList()
        assertThat(restoredRoots.map { it.root }).containsExactlyInAnyOrder(rootVfu, additionalRootVfu)
      }
    }
}

private fun Path.toVirtualFileUrl(project: Project): VirtualFileUrl =
  this.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
