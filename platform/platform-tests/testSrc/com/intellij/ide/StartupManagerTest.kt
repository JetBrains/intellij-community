// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.createHeavyProject
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.runInEdtAndWait
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
    val project = createHeavyProject(fsRule.fs.getPath("/"))
    try {
      val startupManager = StartupManagerImpl.getInstance(project) as StartupManagerImpl
      assertThat(startupManager.postStartupActivityPassed()).isFalse()

      val dumbService = DumbService.getInstance(project) as DumbServiceImpl
      startupManager.registerPostStartupActivity(DumbAwareRunnable {
        runInEdtAndWait {
          dumbService.isDumb = true
        }
        assertThat(dumbService.isDumb).isTrue()
      })

      assertThat(startupManager.postStartupActivityPassed()).isFalse()

      ProjectManagerEx.getInstanceEx().openProject(project)

      val done = CountDownLatch(1)
      startupManager.runAfterOpened {
        assertThat(dumbService.isDumb).isTrue()
        done.countDown()
      }
      done.await(1, TimeUnit.SECONDS)
    }
    finally {
      runInEdtAndWait {
        PlatformTestUtil.forceCloseProjectWithoutSaving(project)
      }
    }
  }
}