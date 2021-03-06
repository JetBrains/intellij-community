// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.StartupActivity
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsRule
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("UsePropertyAccessSyntax")
class StartupManagerTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test(timeout = 5_000)
  //@Test()
  fun runAfterOpenedMustBeDumbAware() {
    val done = CountDownLatch(1)
    val project = ProjectManagerEx.getInstanceEx().openProject(fsRule.fs.getPath("/p"), createTestOpenProjectOptions().copy(beforeOpen = { project ->
      val startupManager = StartupManagerImpl.getInstance(project) as StartupManagerImpl
      assertThat(startupManager.postStartupActivityPassed()).isFalse()

      val dumbService = DumbService.getInstance(project) as DumbServiceImpl
      ExtensionTestUtil.maskExtensions(StartupActivity.POST_STARTUP_ACTIVITY, listOf(StartupActivity.DumbAware {
        runInEdtAndWait {
          dumbService.isDumb = true
        }
        assertThat(dumbService.isDumb).isTrue()
      }, StartupActivity.DumbAware {
        startupManager.runAfterOpened {
          assertThat(dumbService.isDumb).isTrue()
          done.countDown()
        }
      }), project, fireEvents = false)

      assertThat(startupManager.postStartupActivityPassed()).isFalse()
      true
    }))!!
    try {
      done.await(1, TimeUnit.SECONDS)
    }
    finally {
      PlatformTestUtil.forceCloseProjectWithoutSaving(project)
    }
  }
}