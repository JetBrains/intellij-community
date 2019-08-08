// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.startup.StartupActivity.POST_STARTUP_ACTIVITY
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import junit.framework.TestCase
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ProjectOpeningTest : HeavyPlatformTestCase() {
  companion object {
    @JvmStatic
    internal fun closeProject(project: Project?) {
      if (project != null && !project.isDisposed) {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      }
    }
  }

  fun testOpenProjectCancelling() {
    var project: Project? = null
    val activity = MyStartupActivity()
    PlatformTestUtil.maskExtensions(POST_STARTUP_ACTIVITY, listOf(activity), testRootDisposable)
    try {
      val manager = ProjectManagerEx.getInstanceEx()
      val foo = createTempDir("foo")
      project = manager.createProject(null, foo.path)!!
      assertThat(manager.openProject(project)).isFalse
      assertThat(project.isOpen).isFalse
      // 1 on maskExtensions call, second call our call
      assertThat(activity.passedCount!!.get()).isEqualTo(2)
    }
    finally {
      closeProject(project)
    }
  }

  fun testCancelOnLoadingModules() {
    val foo = createTempDir("foo")
    var project: Project? = null
    try {
      val manager: ProjectManagerEx? = ProjectManagerEx.getInstanceEx()
      project = manager!!.createProject(null, foo.path)
      project!!.save()
      closeProject(project)
      ApplicationManager.getApplication().messageBus.connect(testRootDisposable).subscribe(
        ProjectLifecycleListener.TOPIC,
        object : ProjectLifecycleListener {
          override fun projectComponentsInitialized(project: Project) {
            val indicator: ProgressIndicator? = ProgressManager.getInstance().progressIndicator
            TestCase.assertNotNull(indicator)
            indicator!!.cancel()
            indicator.checkCanceled()
          }
        })
      project = manager.loadAndOpenProject(foo)
      TestCase.assertFalse(project!!.isOpen)
      TestCase.assertTrue(project.isDisposed)
    }
    finally {
      closeProject(project)
    }
  }

  fun testIsSameProjectForDirectoryBasedProject() {
    val projectDir = createTempDir("project")
    val dirBasedProject = ProjectManager.getInstance().createProject("project", projectDir.absolutePath)!!
    Disposer.register(testRootDisposable, dirBasedProject)
    TestCase.assertTrue(ProjectUtil.isSameProject(projectDir.absolutePath, dirBasedProject))
    TestCase.assertFalse(ProjectUtil.isSameProject(createTempDir("project2").absolutePath, dirBasedProject))
    val iprFilePath = File(projectDir, "project.ipr")
    TestCase.assertTrue(ProjectUtil.isSameProject(iprFilePath.absolutePath, dirBasedProject))
    val miscXmlFilePath = File(projectDir, ".idea/misc.xml")
    TestCase.assertTrue(ProjectUtil.isSameProject(miscXmlFilePath.absolutePath, dirBasedProject))
    val someOtherFilePath = File(projectDir, "misc.xml")
    TestCase.assertFalse(
      ProjectUtil.isSameProject(someOtherFilePath.absolutePath, dirBasedProject))
  }

  fun testIsSameProjectForFileBasedProject() {
    val projectDir = createTempDir("project")
    val iprFilePath = File(projectDir, "project.ipr")
    val fileBasedProject = ProjectManager.getInstance().createProject(iprFilePath.name, iprFilePath.absolutePath)!!
    disposeOnTearDown(fileBasedProject)
    TestCase.assertTrue(ProjectUtil.isSameProject(projectDir.absolutePath, fileBasedProject))
    TestCase.assertFalse(ProjectUtil.isSameProject(createTempDir("project2").absolutePath, fileBasedProject))
    val iprFilePath2 = File(projectDir, "project2.ipr")
    TestCase.assertFalse(ProjectUtil.isSameProject(iprFilePath2.absolutePath, fileBasedProject))
  }
}

private class MyStartupActivity : StartupActivity, DumbAware {
  val passedCount: AtomicInteger? = AtomicInteger()
  override fun runActivity(project: Project) {
    if (passedCount!!.getAndIncrement() == 0) {
      return
    }
    val indicator: ProgressIndicator? = ProgressManager.getInstance().progressIndicator
    TestCase.assertNotNull(indicator)
    indicator!!.cancel()
  }
}