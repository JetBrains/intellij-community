// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.observable.ActivityInProgressService
import com.intellij.openapi.observable.MarkupBasedActivityInProgressWitness
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

class ActivityInProgressServiceTest {

  class Actor1 : MarkupBasedActivityInProgressWitness() {
    override val presentableName: @Nls String = "actor1"
  }

  class Actor2 : MarkupBasedActivityInProgressWitness() {
    override val presentableName: @Nls String = "actor2"
  }

  class Actor3 : MarkupBasedActivityInProgressWitness() {
    override val presentableName: @Nls String = "actor3"
  }

  val allActors = arrayOf(Actor1::class.java, Actor2::class.java, Actor3::class.java)

  fun chooseRandomActor(): Class<out MarkupBasedActivityInProgressWitness> {
    val index = Random.nextInt(0, allActors.size)
    return allActors[index]
  }

  fun CoroutineScope.createConfigurator(service: ActivityInProgressService): CompletableJob {
    val j = Job()
    val actor = chooseRandomActor()
    launch {
      j.join()
      for (i in 1..5) {
        service.trackConfigurationActivity(actor) {
          delay(10)
        }
      }
    }
    return j
  }

  fun CoroutineScope.createAwaiter(service: ActivityInProgressService): CompletableJob {
    val j = Job()
    val actor = chooseRandomActor()
    launch {
      j.join()
      for (i in 1..5) {
        service.awaitConfiguration(actor)
      }
    }
    return j
  }

  // this test checks the absence of deadlocks in `service.awaitConfiguration`
  // and that internal invariants of `ActivityInProgressService` are not violated
  // the execution is nondeterministic because of coroutines dispatcher and random shuffle
  @RepeatedTest(50)
  fun runTest(): Unit = timeoutRunBlocking {
    coroutineScope {
      val service = ActivityInProgressService(this)
      val actors = (1..5).map { createConfigurator(service) }
      val waiters = (1..5).map { createAwaiter(service) }
      (actors + waiters).shuffled().map { it.complete() }
    }
  }
}