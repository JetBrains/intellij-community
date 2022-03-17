// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.use
import com.intellij.util.io.createDirectories
import com.intellij.util.io.systemIndependentPath
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UsePropertyAccessSyntax")
class ProjectOpeningTest : BareTestFixtureTestCase() {
  @Rule @JvmField val inMemoryFs = InMemoryFsRule()
  @Rule @JvmField val tempDir = TempDirectory()

  @Test fun cancelOnRunPostStartUpActivities() {
    class MyStartupActivity : StartupActivity.DumbAware {
      val passed = AtomicBoolean()

      override fun runActivity(project: Project) {
        passed.set(true)
        ProgressManager.getInstance().progressIndicator!!.cancel()
      }
    }

    val activity = MyStartupActivity()
    val ep = ExtensionPointName<StartupActivity.DumbAware>("com.intellij.startupActivity")
    ExtensionTestUtil.maskExtensions(ep, listOf(activity), testRootDisposable, fireEvents = false)
    val isCancelled = doOpenProject()
    // 1 on maskExtensions call, second call our call
    assertThat(activity.passed.get()).isTrue()
    assertThat(isCancelled).isTrue()
  }

  @Test fun cancelOnLoadingModules() {
    ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
      @Suppress("OVERRIDE_DEPRECATION")
      override fun projectComponentsInitialized(project: Project) {
        val indicator = ProgressManager.getInstance().progressIndicator
        assertThat(indicator).isNotNull()
        indicator!!.cancel()
        indicator.checkCanceled()
      }
    })

    assertThat(doOpenProject()).isTrue()
  }

  private fun doOpenProject(): Boolean {
    var cancelled = false
    ProgressManager.getInstance().run(object : Task.Modal(null, "", true) {
      override fun run(indicator: ProgressIndicator) {
        val project = ProjectManagerEx.getInstanceEx().openProject(inMemoryFs.fs.getPath("/p"), createTestOpenProjectOptions())
        if (project != null) {
          PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        }
        assertThat(project).isNull()
      }

      override fun onCancel() {
        cancelled = true
      }
    })
    return cancelled
  }

  @Test fun isSameProjectForDirectoryBasedProject() {
    val projectDir = inMemoryFs.fs.getPath("/p")
    projectDir.createDirectories()

    val dirBasedProject = ProjectManagerEx.getInstanceEx().newProject(projectDir, createTestOpenProjectOptions())!!
    dirBasedProject.use {
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
    val projectDir = inMemoryFs.fs.getPath("/p")
    projectDir.createDirectories()
    val fileBasedProject = ProjectManagerEx.getInstanceEx().newProject(projectDir.resolve("project.ipr"), createTestOpenProjectOptions())!!
    fileBasedProject.use {
      assertThat(ProjectUtil.isSameProject(projectDir, fileBasedProject)).isTrue()
      assertThat(ProjectUtil.isSameProject(inMemoryFs.fs.getPath("/p2"), fileBasedProject)).isFalse()
      val iprFilePath2 = projectDir.resolve("project2.ipr")
      assertThat(ProjectUtil.isSameProject(iprFilePath2, fileBasedProject)).isFalse()
    }
  }

  @Test fun projectFileLookup() {
    val projectDir = tempDir.root.toPath()
    val projectFile = Files.writeString(projectDir.resolve("project.ipr"), "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<project version=\"4\"/>")
    val project = ProjectUtil.openOrImport(projectDir, OpenProjectTask())
    assertThat(project).isNotNull()
    val projectFilePath = project!!.use { it.projectFilePath }
    assertThat(projectFilePath).isEqualTo(projectFile.systemIndependentPath)
  }
}
