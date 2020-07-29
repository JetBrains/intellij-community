// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runModalTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
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
  val tempDir = TemporaryDirectory()

  @Test
  fun openProjectCancelling() {
    val activity = MyStartupActivity()
    val ep = ExtensionPointName<StartupActivity.DumbAware>("com.intellij.startupActivity")
    ExtensionTestUtil.maskExtensions(ep, listOf(activity), disposableRule.disposable, fireEvents = false)
    val foo = tempDir.newPath()
    ProgressManager.getInstance().run(object : Task.Modal(null, "", true) {
      override fun run(indicator: ProgressIndicator) {
        val project = ProjectManagerEx.getInstanceEx().openProject(foo, createTestOpenProjectOptions())
        if (project != null) {
          PlatformTestUtil.forceCloseProjectWithoutSaving(project)
        }
        assertThat(project).isNull()
      }
    })
    // 1 on maskExtensions call, second call our call
    assertThat(activity.passed.get()).isTrue()
  }

  @Test
  fun cancelOnLoadingModules() {
    val foo = tempDir.newPath()
    var project = createHeavyProject(foo)
    try {
      StoreUtil.saveSettings(project, false)
      closeProject(project)
      ApplicationManager.getApplication().messageBus.connect(disposableRule.disposable).subscribe(ProjectLifecycleListener.TOPIC,
        object : ProjectLifecycleListener {
          override fun projectComponentsInitialized(project: Project) {
            val indicator = ProgressManager.getInstance().progressIndicator
            assertThat(indicator).isNotNull()
            indicator!!.cancel()
            indicator.checkCanceled()
          }
        })
      runModalTask("") {
        project = ProjectManagerEx.getInstanceEx().openProject(foo, createTestOpenProjectOptions())!!
      }
      assertThat(project.isOpen).isFalse()
      assertThat(project.isDisposed).isTrue()
    }
    finally {
      closeProject(project)
    }
  }

  @Test
  fun isSameProjectForDirectoryBasedProject() {
    val projectDir = tempDir.newPath()
    projectDir.createDirectories()

    val dirBasedProject = createHeavyProject(projectDir)
    Disposer.register(disposableRule.disposable, Disposable {
      closeProject(dirBasedProject)
    })

    assertThat(ProjectUtil.isSameProject(projectDir.toAbsolutePath().toString(), dirBasedProject)).isTrue()
    assertThat(ProjectUtil.isSameProject(tempDir.newPath("project2").toAbsolutePath().toString(), dirBasedProject)).isFalse()
    val iprFilePath = projectDir.resolve("project.ipr")
    assertThat(ProjectUtil.isSameProject(iprFilePath.toAbsolutePath().toString(), dirBasedProject)).isTrue()
    val miscXmlFilePath = projectDir.resolve(".idea/misc.xml")
    assertThat(ProjectUtil.isSameProject(miscXmlFilePath.toAbsolutePath().toString(), dirBasedProject)).isTrue()
    val someOtherFilePath = projectDir.resolve("misc.xml")
    assertThat(ProjectUtil.isSameProject(someOtherFilePath.toAbsolutePath().toString(), dirBasedProject)).isFalse()
  }

  @Test
  fun isSameProjectForFileBasedProject() {
    val projectDir = tempDir.newPath()
    projectDir.createDirectories()

    val iprFilePath = projectDir.resolve("project.ipr")
    val fileBasedProject = PlatformTestUtil.loadAndOpenProject(iprFilePath)
    Disposer.register(disposableRule.disposable, Disposable {
      closeProject(fileBasedProject)
    })
    assertThat(ProjectUtil.isSameProject(projectDir.toAbsolutePath().toString(), fileBasedProject)).isTrue()
    assertThat(ProjectUtil.isSameProject(tempDir.newPath("project2").toAbsolutePath().toString(), fileBasedProject)).isFalse()
    val iprFilePath2 = projectDir.resolve("project2.ipr")
    assertThat(ProjectUtil.isSameProject(iprFilePath2.toAbsolutePath().toString(), fileBasedProject)).isFalse()
  }
}

private class MyStartupActivity : StartupActivity.DumbAware {
  val passed = AtomicBoolean()

  override fun runActivity(project: Project) {
    passed.set(true)

    ProgressManager.getInstance().progressIndicator!!.cancel()
  }
}

private fun closeProject(project: Project?) {
  if (project != null && !project.isDisposed) {
    PlatformTestUtil.forceCloseProjectWithoutSaving(project)
  }
}