// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.util.Disposer
import com.intellij.util.ConcurrencyUtil
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Predicate

/**
 * Regression test for IJPL-247543:
 * a `Disposable` service must not stay reachable through `Disposer.getTree()` if the container's
 * `serviceParentDisposable` is disposed concurrently with service registration.
 *
 * The fix lives in `ObjectTree.tryRegister` (which `ObjectTree.register` now delegates to):
 *  - `ObjectTree.executeAll` populates `myDisposedObjects[serviceParentDisposable]` inside the
 *    tree lock, so the `isDisposed(parent)` check at the top of `tryRegister` sees the truth
 *    without any visibility race — even though `serviceParentDisposable` is a plain `Disposable`,
 *    not a `CheckedDisposable`.
 *  - On that branch, `collectStrandedChild(child)` detaches the child (and any subtree the
 *    child's constructor registered under itself via `Disposer.register(this, ...)`) from the
 *    tree, marks it disposed, and stages it so `dispose()` is invoked after the tree lock is
 *    released (mirroring `runWithTrace`'s "collect under lock, dispose after" pattern).
 */
class ServiceContainerDisposalRaceTest {
  private val pluginDescriptor = DefaultPluginDescriptor("service-container-disposal-race-test")
  private val noLeakedRaceService: Predicate<Any> = Predicate { it is RaceTestService }
  private val noLeakedServiceOrChild: Predicate<Any> = Predicate { it is ServiceWithPreRegisteredChild || it is ServiceChild }
  private val noLeakedRootOwner: Predicate<Any> = Predicate {
    it is ServiceOwningDisposerRoot || it is LegacyComponentOwningDisposerRoot || it is OwnedDisposerRoot
  }

  @Test
  fun `getService after serviceParentDisposable is disposed throws and does not leak`() {
    val componentManager = TestComponentManager()
    componentManager.registerService(RaceTestService::class.java, RaceTestService::class.java, pluginDescriptor, false)

    Disposer.dispose(componentManager.serviceParentDisposable)

    assertThrows<Throwable> { componentManager.getService(RaceTestService::class.java) }

    Disposer.getTree().assertNoReferenceKeptInTree(noLeakedRaceService)
  }

  /**
   * The instance itself is brand new when the container tries to adopt it, so `ObjectTree.collectStrandedChild` has
   * nothing to strand — it returns an empty list for an object which never made it into the tree.
   * Since `serviceParentDisposable` is the only path by which a service instance is ever disposed,
   * a rejected registration used to leave the instance — and whatever its constructor made a Disposer ROOT — abandoned
   * in the tree forever (IJPL-247543, `NewMappings` under `ProjectLevelVcsManagerImpl`).
   */
  @Test
  fun `service created after the container disposed its services is disposed instead of being abandoned`() {
    lastRootOwningService = null

    val componentManager = TestComponentManager()
    componentManager.registerService(ServiceOwningDisposerRoot::class.java, ServiceOwningDisposerRoot::class.java, pluginDescriptor, false)

    Disposer.dispose(componentManager.serviceParentDisposable)

    val thrown = assertThrows<Throwable> { componentManager.getService(ServiceOwningDisposerRoot::class.java) }

    val instance = lastRootOwningService
    assertThat(instance).`as`("the service instance must have been constructed").isNotNull
    assertThat(instance!!.ownedRoot.isDisposed)
      .`as`("dispose() of the rejected instance must run, so the Disposer ROOT it owns is disposed too")
      .isTrue

    Disposer.getTree().assertNoReferenceKeptInTree(noLeakedRootOwner)

    assertThat(causeChainOf(thrown))
      .`as`("container must report its state to the caller")
      .hasAtLeastOneElementOfType(AlreadyDisposedException::class.java)
  }

  @Test
  @Timeout(30)
  fun `legacy component created after the container disposed its services is disposed instead of being abandoned`(): Unit =
    runBlocking {
      lastRootOwningComponent = null

      val componentManager = TestComponentManager()
      Disposer.dispose(componentManager.serviceParentDisposable)

      val initializer = ComponentDescriptorInstanceInitializer(
        componentManager = componentManager,
        pluginDescriptor = pluginDescriptor,
        interfaceClass = LegacyComponentOwningDisposerRoot::class.java,
        instanceClassName = LegacyComponentOwningDisposerRoot::class.java.name,
      )
      val thrown = runCatching {
        initializer.createInstance(this, LegacyComponentOwningDisposerRoot::class.java)
      }.exceptionOrNull()

      val instance = lastRootOwningComponent
      assertThat(instance).`as`("the legacy component must have been constructed").isNotNull
      assertThat(instance!!.disposeCount)
        .`as`("disposeComponent() of the rejected component must run exactly once")
        .isEqualTo(1)
      assertThat(instance.ownedRoot.isDisposed)
        .`as`("disposeComponent() must dispose the Disposer ROOT created by initComponent()")
        .isTrue

      Disposer.getTree().assertNoReferenceKeptInTree(noLeakedRootOwner)

      assertThat(thrown).`as`("component creation must fail because its container is disposed").isNotNull
      assertThat(causeChainOf(thrown!!))
        .`as`("container must report its state to the caller")
        .hasAtLeastOneElementOfType(AlreadyDisposedException::class.java)
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

  @Test
  fun `service whose constructor registers a child in Disposer tree must not leak when parent disposed concurrently`() {
    val executor = ConcurrencyUtil.newSingleThreadExecutor("ServiceContainerDisposalRaceTest")
    try {
      repeat(20) { iteration ->
        val componentManager = TestComponentManager()
        componentManager.registerService(
          ServiceWithPreRegisteredChild::class.java,
          ServiceWithPreRegisteredChild::class.java,
          pluginDescriptor,
          false,
        )

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
          componentManager.getService(ServiceWithPreRegisteredChild::class.java)
        }

        proceedDispose.countDown()
        disposeFuture.get(10, TimeUnit.SECONDS)

        Disposer.getTree().assertNoReferenceKeptInTree(noLeakedServiceOrChild)
      }
    }
    finally {
      executor.shutdown()
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue
    }
  }
}

private fun causeChainOf(throwable: Throwable): List<Throwable> {
  val chain = ArrayList<Throwable>()
  var current: Throwable? = throwable
  while (current != null && chain.size < 10 && !chain.contains(current)) {
    chain.add(current)
    current = current.cause
  }
  return chain
}

private class RaceTestService : Disposable {
  override fun dispose() {}
}

private class OwnedDisposerRoot : Disposable {
  var isDisposed: Boolean = false

  override fun dispose() {
    isDisposed = true
  }
}

/**
 * Mirrors `ProjectLevelVcsManagerImpl`: the instance is not in the Disposer tree itself, but its constructor creates
 * a Disposable which becomes an implicit Disposer ROOT and which only `dispose()` tears down.
 */
private class ServiceOwningDisposerRoot : Disposable {
  @JvmField val ownedRoot: OwnedDisposerRoot = OwnedDisposerRoot()

  init {
    // makes `ownedRoot` an implicit Disposer ROOT, exactly as `VcsMappingsWatchRootsModifier` does to `NewMappings`
    Disposer.register(ownedRoot, ServiceChild())
    lastRootOwningService = this
  }

  override fun dispose() {
    Disposer.dispose(ownedRoot)
  }
}

private var lastRootOwningService: ServiceOwningDisposerRoot? = null

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private class LegacyComponentOwningDisposerRoot : BaseComponent {
  lateinit var ownedRoot: OwnedDisposerRoot
    private set

  var disposeCount: Int = 0
    private set

  init {
    lastRootOwningComponent = this
  }

  override fun initComponent() {
    ownedRoot = OwnedDisposerRoot()
    Disposer.register(ownedRoot, ServiceChild())
  }

  override fun disposeComponent() {
    disposeCount++
    Disposer.dispose(ownedRoot)
  }
}

private var lastRootOwningComponent: LegacyComponentOwningDisposerRoot? = null

private class ServiceChild : Disposable {
  override fun dispose() {}
}

private class ServiceWithPreRegisteredChild : Disposable {
  init {
    @Suppress("LeakingThis")
    Disposer.register(this, ServiceChild())
  }

  override fun dispose() {}
}
