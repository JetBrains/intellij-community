// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreUtil
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.util.io.createDirectories
import junit.framework.TestCase
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

@Suppress("UsePropertyAccessSyntax")
class ProjectOpeningTest {
  companion object {
    @JvmStatic
    internal fun closeProject(project: Project?) {
      if (project != null && !project.isDisposed) {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      }
    }

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
    ExtensionTestUtil.maskExtensions(StartupActivity.POST_STARTUP_ACTIVITY, listOf(activity), disposableRule.disposable, fireEvents = false)
    val manager = ProjectManagerEx.getInstanceEx()
    val foo = tempDir.newPath()
    val project = manager.createProject(null, foo.toString())!!
    try {
      runInEdtAndWait {
        assertThat(manager.openProject(project)).isFalse()
      }
      assertThat(project.isOpen).isFalse()
      // 1 on maskExtensions call, second call our call
      assertThat(activity.passed.get()).isTrue()
    }
    finally {
      runInEdtAndWait {
        closeProject(project)
      }
    }
  }

  @Test
  fun cancelOnLoadingModules() {
    val foo = tempDir.newPath()
    val manager: ProjectManagerEx? = ProjectManagerEx.getInstanceEx()
    var project = manager!!.createProject(null, foo.toString())!!
    try {
      StoreUtil.saveSettings(project, false)
      runInEdtAndWait {
        closeProject(project)
      }
      ApplicationManager.getApplication().messageBus.connect(disposableRule.disposable).subscribe(ProjectLifecycleListener.TOPIC,
                                                                                                  object : ProjectLifecycleListener {
                                                                                                    override fun projectComponentsInitialized(project: Project) {
                                                                                                      val indicator: ProgressIndicator? = ProgressManager.getInstance().progressIndicator
                                                                                                      TestCase.assertNotNull(indicator)
                                                                                                      indicator!!.cancel()
                                                                                                      indicator.checkCanceled()
                                                                                                    }
                                                                                                  })
      runInEdtAndWait {
        project = manager.loadAndOpenProject(foo)!!
      }
      assertThat(project.isOpen).isFalse()
      assertThat(project.isDisposed).isTrue()
    }
    finally {
      runInEdtAndWait {
        closeProject(project)
      }
    }
  }

  @Test
  fun isSameProjectForDirectoryBasedProject() {
    val projectDir = tempDir.newPath()
    projectDir.createDirectories()

    val dirBasedProject = ProjectManager.getInstance().createProject("project", projectDir.toAbsolutePath().toString())!!
    Disposer.register(disposableRule.disposable, Disposable {
      runInEdtAndWait { closeProject(dirBasedProject) }
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
    val fileBasedProject = ProjectManager.getInstance().createProject(iprFilePath.fileName.toString(), iprFilePath.toAbsolutePath().toString())!!
    Disposer.register(disposableRule.disposable, Disposable {
      runInEdtAndWait { closeProject(fileBasedProject) }
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