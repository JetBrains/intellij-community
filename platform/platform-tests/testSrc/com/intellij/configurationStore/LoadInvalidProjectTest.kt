// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.ide.impl.TrustedPaths
import com.intellij.notification.Notification
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.openapi.module.ConfigurationErrorDescription
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.ProjectLoadingErrorsHeadlessNotifier
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.IoTestUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.project.stateStore
import com.intellij.testFramework.*
import com.intellij.util.io.assertMatches
import com.intellij.util.io.directoryContentOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Path
import java.nio.file.Paths

private const val untrustedJpsProjectNotificationPart = "Configuration files aren't loaded for projects opened in the safe mode."

class LoadInvalidProjectTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()

    private val testDataRoot: Path
      get() = Paths.get(PathManagerEx.getCommunityHomePath()).resolve("platform/platform-tests/testData/configurationStore/invalid")
  }

  @JvmField
  @Rule
  val tempDirectory = TemporaryDirectory()

  @JvmField
  @Rule
  val disposable = DisposableRule()
  private val errors = ArrayList<ConfigurationErrorDescription>()

  private fun getNotifications(project: Project, groupId: String = "Project Loading Error"): List<Notification> {
    val notifications = NotificationsManager.getNotificationsManager().getNotificationsOfType(Notification::class.java, project)
    return notifications.filter { it.groupId == groupId }.toList()
  }

  @Before
  fun setUp() {
    ProjectLoadingErrorsHeadlessNotifier.setErrorHandler(disposable.disposable, errors::add)
  }

  @Test
  fun `load empty iml`() = runBlocking {
    loadProjectAndCheckResults("empty-iml-file") { project ->
      assertContainsSingleModuleFoo(project)
      assertThat(errors.single().description).contains("foo.iml")
    }
  }

  @Test
  fun `malformed xml in iml`() = runBlocking {
    loadProjectAndCheckResults("malformed-xml-in-iml") { project ->
      assertContainsSingleModuleFoo(project)
      assertThat(errors.single().description).contains("foo.iml")
    }
  }

  @Test
  fun `unknown classpath provider in iml`() = runBlocking {
    loadProjectAndCheckResults("unknown-classpath-provider-in-iml") { project ->
      assertContainsSingleModuleFoo(project)
      assertThat(errors.single().description).contains("foo.iml")
    }
  }

  @Test
  fun `no iml file`() = runBlocking {
    loadProjectAndCheckResults("no-iml-file") { project ->
      //this repeats behavior in the old model: if iml files doesn't exist we create a module with empty configuration and report no errors
      assertThat(ModuleManager.getInstance(project).modules.single().name).isEqualTo("foo")
      assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `missing library tag in module library dependency`() = runBlocking {
    loadProjectAndCheckResults("missing-module-library-tag-in-iml") { project ->
      assertContainsSingleModuleFoo(project)
      assertThat(errors.single().description).contains("foo.iml")
    }
  }

  private fun assertContainsSingleModuleFoo(project: Project) {
    assertThat(ModuleManager.getInstance(project).modules).hasSize(1)
    assertThat(WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java).single().name).isEqualTo("foo")
  }

  @Test
  fun `empty library xml`() = runBlocking {
    loadProjectAndCheckResults("empty-library-xml") { project ->
      //this repeats behavior in the old model: if library configuration file cannot be parsed it's silently ignored
      assertThat(LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries).isEmpty()
      assertThat(errors).isEmpty()
    }
  }

  @Test
  fun `duplicating libraries`() = runBlocking {
    loadProjectAndCheckResults("duplicating-libraries") { project ->
      assertThat(LibraryTablesRegistrar.getInstance().getLibraryTable(project).libraries.single().name).isEqualTo("foo")
      assertThat(errors).isEmpty()

      project.stateStore.save()
      val expected = directoryContentOf(testDataRoot.resolve("duplicating-libraries-fixed"))
          .mergeWith(directoryContentOf(testDataRoot.resolve ("common")))
      val projectDir = project.stateStore.directoryStorePath!!.parent
      projectDir.assertMatches(expected)
    }
  }

  private fun loadUntrustedProjectCheckResults(
    projectTemplate: Path,
    task: suspend (Project) -> Unit,
  ) {
    fun createUntrustedProject(targetDir: VirtualFile): Path {
      val projectDir = VfsUtil.virtualToIoFile(targetDir)
      FileUtil.copyDir(projectTemplate.toFile(), projectDir)
      VfsUtil.markDirtyAndRefresh(false, true, true, targetDir)
      val projectDirPath = projectDir.toPath()
      TrustedPaths.getInstance().setProjectPathTrusted(projectDirPath, false)
      return projectDirPath
    }
    runBlocking {
      createOrLoadProject(tempDirectory, ::createUntrustedProject, loadComponentState = true, useDefaultProjectSettings = false, task = task)
    }
  }

  private suspend fun checkUntrustedModuleIsNotLoaded(project: Project, moduleName: String) {
    val moduleEntities = blockingContext {
      WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java)
    }

    assertThat(moduleEntities.find { module -> module.name == moduleName }).isNull()
  }

  private fun checkProjectHasNotification(project: Project, contentBeginning: String) {
    val notifications = getNotifications(project)
    assertThat(notifications).hasSize(1)
    assertThat(notifications.single().content).startsWith(contentBeginning)
  }

  @Test
  fun `remote iml paths must not be loaded in untrusted projects`() {
    IoTestUtil.assumeWindows()
    loadUntrustedProjectCheckResults(testDataRoot.resolve("remote-iml-path")) { project ->
      checkProjectHasNotification(project, untrustedJpsProjectNotificationPart)
      checkUntrustedModuleIsNotLoaded(project, "foo") // 'foo' is not a random name, but a concrete module name from test data
    }
  }

  @Test
  fun `remote iml paths must not be loaded in untrusted projects (with protocol)`() {
    loadUntrustedProjectCheckResults(testDataRoot.resolve("remote-iml-path-with-protocol")) { project ->
      checkProjectHasNotification(project, untrustedJpsProjectNotificationPart)
      checkUntrustedModuleIsNotLoaded(project, "foo") // 'foo' is not a random name, but a concrete module name from test data
    }
  }

  @Test
  fun `remote roots must not be loaded in untrusted projects`() {
    IoTestUtil.assumeWindows()
    loadUntrustedProjectCheckResults(testDataRoot.resolve("remote-roots")) { project ->
      checkProjectHasNotification(project, untrustedJpsProjectNotificationPart)
      checkUntrustedModuleIsNotLoaded(project, "foo") // 'foo' is not a random name, but a concrete module name from test data
    }
  }

  @Test
  fun `remote roots must not be loaded in untrusted projects (with protocol)`() {
    loadUntrustedProjectCheckResults(testDataRoot.resolve("remote-roots-with-protocol")) { project ->
      checkProjectHasNotification(project, untrustedJpsProjectNotificationPart)
      checkUntrustedModuleIsNotLoaded(project, "foo") // 'foo' is not a random name, but a concrete module name from test data
    }
  }

  private suspend fun loadProjectAndCheckResults(testDataDirName: String, checkProject: suspend (Project) -> Unit) {
    return loadProjectAndCheckResults(projectPaths = listOf(testDataRoot.resolve("common"), testDataRoot.resolve(testDataDirName)),
                                      tempDirectory = tempDirectory,
                                      checkProject = checkProject)
  }
}
