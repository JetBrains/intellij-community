// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbServiceImpl.Companion.IDEA_FORCE_DUMB_QUEUE_TASKS
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.ProjectRule
import com.intellij.util.SystemProperties
import org.junit.*
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class ProjectRootManagerOnEndOfDumbModeIncrementerTest {
  companion object {
    @JvmField
    @ClassRule
    val projectRule = ProjectRule(true, false, null)
    private var prevDumbQueueTasks: String? = null
    private var prevIgnoreHeadless: String? = null


    @BeforeClass
    @JvmStatic
    fun beforeAll() {
      prevDumbQueueTasks = System.setProperty(IDEA_FORCE_DUMB_QUEUE_TASKS, "true")
      prevIgnoreHeadless = System.setProperty("intellij.progress.task.ignoreHeadless", "true")
    }

    @AfterClass
    @JvmStatic
    fun afterAll() {
      SystemProperties.setProperty(IDEA_FORCE_DUMB_QUEUE_TASKS, prevDumbQueueTasks)
      SystemProperties.setProperty("intellij.progress.task.ignoreHeadless", prevIgnoreHeadless)
    }
  }

  private lateinit var project: Project

  @Before
  fun setUp() {
    project = projectRule.project
    project.service<DumbService>().waitForSmartMode()
  }

  @Test
  fun `test ProjectRootManager is incremented in the end of dumb mode`() {
    val projectRootManager = project.service<ProjectRootManager>()
    val modCountBefore = projectRootManager.modificationCount

    val dumbService = project.service<DumbService>()
    val taskComplete = CountDownLatch(1)
    dumbService.queueTask(object : DumbModeTask() {
      override fun performInDumbMode(indicator: ProgressIndicator) = taskComplete.countDown()
    })

    val completed = taskComplete.await(1, TimeUnit.SECONDS)
    assertTrue(completed, "Task didn't complete in 1 second")
    dumbService.waitForSmartMode()

    val modCountAfter = projectRootManager.modificationCount
    assertTrue(modCountBefore < modCountAfter, "ProjectRootManager should increment at leat by 1")
  }
}