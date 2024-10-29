// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.impl.ProjectUtilCore
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.InitProjectActivity
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.useProject
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
}
