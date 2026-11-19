// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.OpenProjectImplOptions
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.DelegatingComponentManagerEx
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.Key
import com.intellij.openapi.workspace.ProjectWorkspace
import com.intellij.openapi.workspace.impl.ProjectWorkspaceComponentManager
import com.intellij.openapi.workspace.impl.ProjectWorkspaceFactory
import com.intellij.openapi.workspace.impl.WorkspaceAsProject
import com.intellij.openapi.workspace.workspace
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

/**
 * The binding of a project to its [ProjectWorkspace], see `ProjectWorkspaceManagerImpl.bindProject`.
 */
@TestApplication
@Timeout(120)
internal class ProjectWorkspaceBindingTest {
  private val directoryFixture = tempPathFixture()

  @TestDisposable
  lateinit var disposable: Disposable

  @AfterEach
  fun closeProjects() {
    for (project in ProjectManager.getInstance().openProjects) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }

  @Test
  fun `a guest of a plain owner has a workspace of its own`(): Unit = timeoutRunBlocking {
    val owner = openOwner("owner")
    val guest = openGuest("guest", owner)

    val workspace = guest.workspace
    assertThat(workspace).isInstanceOf(WorkspaceAsProject::class.java)
    assertThat(workspace).isNotSameAs(owner.workspace)
    assertThat(workspace.projects).containsExactly(guest)
  }

  @Test
  fun `a guest has the workspace of its owner from its creation to its dispose`(): Unit = timeoutRunBlocking {
    registerHostFactory()
    val owner = openOwner("owner")
    val atBeforeInit = ArrayList<ProjectWorkspace>()
    val options = OpenProjectTask(createTestOpenProjectOptions()) {
      isNewProject = true
      implOptions = OpenProjectImplOptions(frameOwner = owner)
      beforeInitTasks += { atBeforeInit.add(it.workspace) }
    }
    val guest = open(directory("guest"), options)
    val atDispose = ArrayList<ProjectWorkspace>()
    guest.service<WorkspaceRecordingProjectService>().onDispose = { atDispose.add(it.workspace) }

    PlatformTestUtil.forceCloseProjectWithoutSaving(guest)

    val ownerWorkspace = owner.workspace
    assertThat(ownerWorkspace).isInstanceOf(TestHostWorkspace::class.java)
    assertThat(atBeforeInit).containsExactly(ownerWorkspace)
    assertThat(atDispose).containsExactly(ownerWorkspace)
  }

  private fun registerHostFactory() {
    val ep = ExtensionPointName<ProjectWorkspaceFactory>("com.intellij.projectWorkspaceFactory")
    ExtensionTestUtil.maskExtensions(ep, listOf(TestHostWorkspaceFactory()), disposable, fireEvents = false)
  }

  private suspend fun openOwner(name: String): Project {
    return open(directory(name), OpenProjectTask(createTestOpenProjectOptions()) { isNewProject = true })
  }

  private suspend fun openGuest(name: String, owner: Project): Project = open(directory(name), guestOptions(owner))

  private fun guestOptions(frameOwner: Project): OpenProjectTask {
    return OpenProjectTask(createTestOpenProjectOptions()) {
      isNewProject = true
      implOptions = OpenProjectImplOptions(frameOwner = frameOwner)
    }
  }

  private suspend fun open(path: Path, options: OpenProjectTask): Project {
    return checkNotNull(ProjectManagerEx.getInstanceEx().openProjectAsync(path, options)) { "No project opened at $path" }
  }

  private suspend fun directory(name: String): Path = withContext(Dispatchers.IO) {
    directoryFixture.get().resolve(name).createDirectories()
  }
}

/** Gives a [TestHostWorkspace] for the directory `owner`. */
private class TestHostWorkspaceFactory : ProjectWorkspaceFactory {
  override fun createWorkspace(project: Project, identityFile: Path): ProjectWorkspace? {
    return if (identityFile.name == "owner") TestHostWorkspace(project) else null
  }
}

/** A workspace of several projects, of the form of [WorkspaceAsProject]. */
private class TestHostWorkspace(project: Project) : ProjectWorkspace, DelegatingComponentManagerEx {
  private val container = ProjectWorkspaceComponentManager(hostProject = project)

  override val delegateComponentManager: ComponentManagerEx
    get() = container

  override val projects: List<Project> = listOf(project)

  init {
    container.initForWorkspace(this)
  }

  override fun <T : Any?> getUserData(key: Key<T?>): T? = container.getUserData(key)

  override fun <T : Any?> putUserData(key: Key<T?>, value: T?) {
    container.putUserData(key, value)
  }
}

@Service(Service.Level.PROJECT)
private class WorkspaceRecordingProjectService(private val project: Project) : Disposable {
  var onDispose: ((Project) -> Unit)? = null

  override fun dispose() {
    onDispose?.invoke(project)
  }
}
