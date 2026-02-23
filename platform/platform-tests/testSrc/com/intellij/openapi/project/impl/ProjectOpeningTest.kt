// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesProvider
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService
import com.intellij.openapi.wm.ex.ProjectFrameCapability
import com.intellij.openapi.wm.ex.ProjectFrameUiPolicy
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.useProject
import com.intellij.util.application
import com.intellij.util.io.createDirectories
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.invariantSeparatorsPathString

class ProjectOpeningTest : BareTestFixtureTestCase() {
  @Rule @JvmField val inMemoryFs = InMemoryFsRule()
  @Rule @JvmField val tempDir = TempDirectory()

  @After fun cleanup() {
    val projects = ProjectUtilCore.getOpenProjects()
    if (projects.isNotEmpty()) {
      val message = "Leaked projects: ${projects.toList()}"
      projects.forEach(PlatformTestUtil::forceCloseProjectWithoutSaving)
      Assert.fail(message)
    }
  }

  @Test fun cancelOnRunPostStartUpActivities() {
    val passed = AtomicBoolean()
    var job: Job? = null
    val activity = object : InitProjectActivity {
      override suspend fun run(project: Project) {
        passed.set(true)
        job!!.cancel("test")
      }
    }
    val ep = ExtensionPointName<InitProjectActivity>("com.intellij.initProjectActivity")
    ExtensionTestUtil.maskExtensions(ep, listOf(activity), testRootDisposable, fireEvents = false)
    runBlocking {
      job = launch {
        assertProjectOpenIsCancelled(createTestOpenProjectOptions())
      }
    }
    assertThat(passed.get()).isTrue()
  }

  @Test fun cancelOnLoadingModules() {
    runBlocking {
      var job: Job? = null
      job = launch {
        assertProjectOpenIsCancelled(createTestOpenProjectOptions(beforeOpen = { job!!.cancel("test") }))
      }
    }
  }

  @Test
  fun suppressBackgroundActivitiesSkipsDirectoryProjectConfigurators() {
    val configuratorInvoked = AtomicBoolean(false)
    val configurator = object : DirectoryProjectConfigurator {
      override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
        configuratorInvoked.set(true)
      }
    }
    ExtensionTestUtil.maskExtensions(
      ExtensionPointName.create<DirectoryProjectConfigurator>("com.intellij.directoryProjectConfigurator"),
      listOf(configurator),
      testRootDisposable,
      fireEvents = false,
    )

    val baselineProjectDir = tempDir.root.toPath().resolve("baseline-configurators").createDirectories()
    val baselineProject = runBlocking {
      ProjectManagerEx.getInstanceEx().openProjectAsync(
        baselineProjectDir,
        OpenProjectTask {
          isNewProject = true
          runConfigurators = true
        }
      )
    }
    assertThat(baselineProject).isNotNull()
    baselineProject!!.useProject { }
    assertThat(configuratorInvoked.get()).isTrue()

    configuratorInvoked.set(false)
    ExtensionTestUtil.maskExtensions(
      ProjectFrameCapabilitiesService.EP_NAME,
      listOf(object : ProjectFrameCapabilitiesProvider {
        override fun getCapabilities(project: Project): Set<ProjectFrameCapability> {
          return setOf(ProjectFrameCapability.SUPPRESS_BACKGROUND_ACTIVITIES)
        }

        override fun getUiPolicy(project: Project, capabilities: Set<ProjectFrameCapability>): ProjectFrameUiPolicy? {
          return null
        }
      }),
      testRootDisposable,
      fireEvents = false,
    )

    val suppressedProjectDir = tempDir.root.toPath().resolve("suppressed-configurators").createDirectories()
    val suppressedProject = runBlocking {
      ProjectManagerEx.getInstanceEx().openProjectAsync(
        suppressedProjectDir,
        OpenProjectTask {
          isNewProject = true
          runConfigurators = true
        }
      )
    }
    assertThat(suppressedProject).isNotNull()
    suppressedProject!!.useProject { }
    assertThat(configuratorInvoked.get()).isFalse()
  }

  private suspend fun assertProjectOpenIsCancelled(options: OpenProjectTask) {
    val project = ProjectManagerEx.getInstanceEx().openProjectAsync(inMemoryFs.fs.getPath("/p"), options)
    if (project != null) {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
    assertThat(project).isNull()
  }

  @Test fun isSameProjectForDirectoryBasedProject() {
    val projectDir = inMemoryFs.fs.getPath("/p").createDirectories()
    val dirBasedProject = ProjectManagerEx.getInstanceEx().newProject(projectDir, createTestOpenProjectOptions())!!
    dirBasedProject.useProject {
      assertThat(ProjectUtil.isSameProject(projectDir, dirBasedProject)).isTrue()
      assertThat(ProjectUtil.isSameProject(inMemoryFs.fs.getPath("/p2"), dirBasedProject)).isFalse()
      val iprFilePath = projectDir.resolve("project.ipr")
      assertThat(ProjectUtil.isSameProject(iprFilePath, dirBasedProject)).isTrue()
      val miscXmlFilePath = projectDir.resolve(".idea/misc.xml")
      assertThat(ProjectUtil.isSameProject(miscXmlFilePath, dirBasedProject)).isTrue()
      val someOtherFilePath = projectDir.resolve("misc.xml")
      assertThat(ProjectUtil.isSameProject(someOtherFilePath, dirBasedProject)).isFalse()
    }
  }

  @Test fun isSameProjectForFileBasedProject() {
    val projectDir = inMemoryFs.fs.getPath("/p").createDirectories()
    val fileBasedProject = ProjectManagerEx.getInstanceEx().newProject(projectDir.resolve("project.ipr"), createTestOpenProjectOptions())!!
    fileBasedProject.useProject {
      assertThat(ProjectUtil.isSameProject(projectDir, fileBasedProject)).isTrue()
      assertThat(ProjectUtil.isSameProject(inMemoryFs.fs.getPath("/p2"), fileBasedProject)).isFalse()
      val iprFilePath2 = projectDir.resolve("project2.ipr")
      assertThat(ProjectUtil.isSameProject(iprFilePath2, fileBasedProject)).isFalse()
    }
  }

  @Test fun projectFileLookup() {
    val projectDir = tempDir.root.toPath()
    val projectFile = Files.writeString(projectDir.resolve("project.ipr"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\"/>")
    val project = runBlocking {
      ProjectUtil.openOrImportAsync(projectDir, OpenProjectTask())
    }
    assertThat(project).isNotNull()
    val projectFilePath = project!!.useProject { it.projectFilePath }
    assertThat(projectFilePath).isEqualTo(projectFile.invariantSeparatorsPathString)
  }

  @Test fun projectFileLookupSync() {
    val projectDir = tempDir.root.toPath()
    val projectFile = Files.writeString(projectDir.resolve("project.ipr"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\"/>")
    val project = ProjectUtil.openOrImport(projectDir, OpenProjectTask())
    assertThat(project).isNotNull()
    val projectFilePath = project!!.useProject { it.projectFilePath }
    assertThat(projectFilePath).isEqualTo(projectFile.invariantSeparatorsPathString)
  }

  @Suppress("removal")
  @Test
  fun `project listener is called under write-intent lock`() {
    val listenerRunsUnderWriteIntent = AtomicBoolean(false)
    val projectDir = tempDir.root.toPath()
    application.messageBus.connect(testRootDisposable).subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
      override fun projectOpened(project: Project) {
        listenerRunsUnderWriteIntent.set(application.isWriteIntentLockAcquired)
      }
    })
    val project = ProjectUtil.openOrImport(projectDir, OpenProjectTask())
    project?.useProject {  } // closing this project
    assertThat(listenerRunsUnderWriteIntent.get()).isTrue()
  }
}
