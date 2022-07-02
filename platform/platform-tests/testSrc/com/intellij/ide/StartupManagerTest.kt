// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.ide.startup.impl.StartupManagerImpl
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.startup.StartupManager
import com.intellij.testFramework.*
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.rules.InMemoryFsExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Suppress("UsePropertyAccessSyntax")
class StartupManagerTest {
  companion object {
    @JvmField
    @RegisterExtension
    val appRule = ApplicationExtension()
  }

  @JvmField
  @RegisterExtension
  val fsRule = InMemoryFsExtension()

  @Test
  @Timeout(5, unit = TimeUnit.SECONDS)
  fun runAfterOpenedMustBeDumbAware(): Unit = runBlocking {
    val done = CountDownLatch(1)
    val project = ProjectManagerEx.getInstanceEx().openProjectAsync(
      projectStoreBaseDir = fsRule.fs.getPath("/p"),
      options = createTestOpenProjectOptions().copy(
        beforeOpen = { project ->
          val startupManager = StartupManager.getInstance(project) as StartupManagerImpl
          assertThat(startupManager.postStartupActivityPassed()).isFalse()

          val dumbService = DumbService.getInstance(project) as DumbServiceImpl
          ExtensionTestUtil.maskExtensions(
            StartupActivity.POST_STARTUP_ACTIVITY,
            newExtensions = listOf(
              object : ProjectPostStartupActivity {
                override suspend fun execute(project: Project) {
                  withContext(Dispatchers.EDT) {
                    dumbService.isDumb = true
                  }
                  assertThat(dumbService.isDumb).isTrue()
                }
              },
              object : StartupActivity, DumbAware {
                override fun runActivity(project: Project) {
                  startupManager.runAfterOpened {
                    assertThat(dumbService.isDumb).isTrue()
                    done.countDown()
                  }
                }
              },
            ),
            parentDisposable = project,
            fireEvents = false
          )

          assertThat(startupManager.postStartupActivityPassed()).isFalse()
          true
        }
      )
    )!!
    try {
      done.await(1, TimeUnit.SECONDS)
    }
    finally {
      ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(project)
    }
  }
}