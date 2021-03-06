// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.createDirectories
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UsePropertyAccessSyntax")
class ProjectOpeningTest {
  companion object {
    @ClassRule
    @JvmField
    val appRule = ApplicationRule()
  }

  @Rule
  @JvmField
  val disposableRule = DisposableRule()

  @Rule
  @JvmField
  val inMemoryFs = InMemoryFsRule()

  @Test
  fun cancelOnRunPostStartUpActivities() {
    val activity = MyStartupActivity()
    val ep = ExtensionPointName<StartupActivity.DumbAware>("com.intellij.startupActivity")
    ExtensionTestUtil.maskExtensions(ep, listOf(activity), disposableRule.disposable, fireEvents = false)
    val isCancelled = doOpenProject()
    // 1 on maskExtensions call, second call our call
    assertThat(activity.passed.get()).isTrue()
    assertThat(isCancelled).isTrue()
  }

  @Test
  fun cancelOnLoadingModules() {
    ApplicationManager.getApplication().messageBus.connect(disposableRule.disposable).subscribe(ProjectLifecycleListener.TOPIC, object : ProjectLifecycleListener {
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

  @Test
  fun isSameProjectForDirectoryBasedProject() {
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

  @Test
  fun isSameProjectForFileBasedProject() {
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
}

private class MyStartupActivity : StartupActivity.DumbAware {
  val passed = AtomicBoolean()

  override fun runActivity(project: Project) {
    passed.set(true)

    ProgressManager.getInstance().progressIndicator!!.cancel()
  }
}