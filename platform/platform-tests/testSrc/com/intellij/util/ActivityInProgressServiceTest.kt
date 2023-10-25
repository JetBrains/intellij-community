// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.project.Project
import com.intellij.platform.backend.observation.ActivityInProgressWitness
import com.intellij.platform.backend.observation.MarkupBasedActivityInProgressWitness
import com.intellij.platform.backend.observation.Observation
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.junit.ClassRule
import kotlin.random.Random
import kotlin.reflect.KClass

class ActivityInProgressServiceTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  private val projectRule = ProjectRule()

  class Actor1 : MarkupBasedActivityInProgressWitness() {
    override val presentableName: @Nls String = "actor1"
  }

  class Actor2 : MarkupBasedActivityInProgressWitness() {
    override val presentableName: @Nls String = "actor2"
  }

  class Actor3 : MarkupBasedActivityInProgressWitness() {
    override val presentableName: @Nls String = "actor3"
  }

  val allActors = arrayOf(Actor1::class, Actor2::class, Actor3::class)

  fun registerExtensions(project: Project) {
    ExtensionTestUtil.maskExtensions(ActivityInProgressWitness.EP_NAME, listOf(Actor1(), Actor2(), Actor3()), project,
                                     areaInstance = application)
  }

  fun chooseRandomActor(): KClass<out MarkupBasedActivityInProgressWitness> {
    val index = Random.nextInt(0, allActors.size)
    return allActors[index]
  }

  fun CoroutineScope.createConfigurator(project: Project): CompletableJob {
    val j = Job()
    val actor = chooseRandomActor()
    launch {
      j.join()
      repeat(5) {
        project.trackActivity(actor) {
          delay(10)
        }
      }
    }
    return j
  }

  fun CoroutineScope.createAwaiter(project: Project): CompletableJob {
    val j = Job()
    launch {
      j.join()
      repeat(5) {
        Observation.awaitConfiguration(project)
      }
    }
    return j
  }

  // this test checks the absence of deadlocks in `service.awaitConfiguration`
  // and that internal invariants of `ActivityInProgressService` are not violated
  // the execution is nondeterministic because of coroutines dispatcher and random shuffle
  fun test(): Unit = timeoutRunBlocking {
    val project = projectRule.project
    registerExtensions(project)
    repeat(20) {
      coroutineScope {
        val actors = (1..5).map { createConfigurator(project) }
        val waiters = (1..5).map { createAwaiter(project) }
        (actors + waiters).shuffled().map { it.complete() }
      }
    }
  }
}