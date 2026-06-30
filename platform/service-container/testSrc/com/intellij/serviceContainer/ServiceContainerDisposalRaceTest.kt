// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.util.ConcurrencyUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

/**
 * Regression test for IJPL-247543:
 * a `Disposable` service must not stay reachable through `Disposer.getTree()` if the container's
 * `serviceParentDisposable` is disposed concurrently with service registration.
 *
 * The fix relies on:
 *  - `ComponentManagerImpl.serviceParentDisposable` being a `CheckedDisposable`
 *    (so `ObjectTree.executeAll` marks it disposed inside the tree lock); and
 *  - `ServiceInstanceInitializer.createInstance` using `Disposer.tryRegister` and
 *    disposing the just-created instance when registration fails.
 */
class ServiceContainerDisposalRaceTest {
  private val pluginDescriptor = DefaultPluginDescriptor("service-container-disposal-race-test")
  private val noLeakedRaceService: Predicate<Any> = Predicate { it is RaceTestService }

  @Test
  fun `getService after serviceParentDisposable is disposed throws and does not leak`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(RaceTestService::class.java, RaceTestService::class.java, pluginDescriptor, false)

    Disposer.dispose(componentManager.serviceParentDisposable)

    assertThrows<Throwable> { componentManager.getService(RaceTestService::class.java) }

    Disposer.getTree().assertNoReferenceKeptInTree(noLeakedRaceService)
  }

  @Test
  fun `concurrent getService during serviceParentDisposable disposal must not leak the instance`() {
    // Deterministic race window: a Disposable.Parent child blocks Thread A (the disposing thread)
    // inside beforeTreeDispose() AFTER ObjectTree.executeAll has already marked the
    // CheckedDisposable parent as disposed under the tree lock. Thread B (this thread) then
    // requests the service; tryRegister must observe isDisposed=true and the service must not
    // end up in Disposer.getTree().
    val executor = ConcurrencyUtil.newSingleThreadExecutor("ServiceContainerDisposalRaceTest")
    try {
      repeat(20) { iteration ->
        val componentManager = TestComponentManager()
        componentManager.registerService(RaceTestService::class.java, RaceTestService::class.java, pluginDescriptor, false)

        val inBeforeTreeDispose = CountDownLatch(1)
        val proceedDispose = CountDownLatch(1)

        val blocker = object : Disposable.Parent {
          override fun beforeTreeDispose() {
            inBeforeTreeDispose.countDown()
            assertThat(proceedDispose.await(10, TimeUnit.SECONDS))
              .`as`("Blocker should be unblocked within timeout (iteration=$iteration)")
              .isTrue
          }

          override fun dispose() {}
        }
        Disposer.register(componentManager.serviceParentDisposable, blocker)

        val disposeFuture = executor.submit {
          Disposer.dispose(componentManager.serviceParentDisposable)
        }

        assertThat(inBeforeTreeDispose.await(10, TimeUnit.SECONDS))
          .`as`("Disposing thread should reach beforeTreeDispose within timeout (iteration=$iteration)")
          .isTrue

        assertThrows<Throwable>("getService should fail because serviceParentDisposable is already marked disposed (iteration=$iteration)") {
          componentManager.getService(RaceTestService::class.java)
        }

        proceedDispose.countDown()
        disposeFuture.get(10, TimeUnit.SECONDS)

        Disposer.getTree().assertNoReferenceKeptInTree(noLeakedRaceService)
      }
    }
    finally {
      executor.shutdown()
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue
    }
  }
}

private class RaceTestService : Disposable {
  override fun dispose() {}
}
