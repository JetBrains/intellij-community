// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.util.coroutines.attachAsChildTo
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*

internal class TestFixtureImpl<T>(
  private val debugString: String,
  initializer: TestFixtureInitializer<T>,
) : TestFixture<T> {

  override fun toString(): String {
    return debugString
  }

  // TestFixtureInitializer<T> | Deferred<ScopedValue<T>>
  private var _state: Any = initializer

  override fun get(): T {
    try {
      @Suppress("UNCHECKED_CAST")
      val deferred = _state as Deferred<ScopedValue<T>>
      @OptIn(ExperimentalCoroutinesApi::class)
      return deferred.getCompleted().first
    }
    catch (t: CancellationException) {
      throw IllegalStateException(t)
    }
    catch (t: Throwable) {
      throw t
    }
  }

  fun init(testScope: CoroutineScope, context: TestContext): Deferred<ScopedValue<T>> {
    val state = _state
    if (state !is TestFixtureInitializer<*>) {
      @Suppress("UNCHECKED_CAST")
      return state as Deferred<ScopedValue<T>>
    }
    return initSync(testScope, context)
  }

  @Synchronized // for simplicity; can be made atomic if needed
  private fun initSync(testScope: CoroutineScope, context: TestContext): Deferred<ScopedValue<T>> {
    val state = _state
    if (state !is TestFixtureInitializer<*>) {
      @Suppress("UNCHECKED_CAST")
      return state as Deferred<ScopedValue<T>>
    }
    val deferred = CompletableDeferred<ScopedValue<T>>(parent = testScope.coroutineContext.job)
    deferred.invokeOnCompletion { throwable ->
      if (throwable != null) {
        deferred.cancel(CancellationException(throwable.message, throwable))
      }
    }
    _state = deferred
    testScope.launch(CoroutineName(debugString)) {
      @Suppress("UNCHECKED_CAST")
      val initializer = state as TestFixtureInitializer<T>
      val scope = TestFixtureInitializerReceiverImpl<T>(testScope, context)
      val (fixture, tearDown) = try {
        with(initializer) {
          scope.initFixture(context) as InitializedTestFixtureData<T>
        }
      }
      catch (t: Throwable) {
        deferred.completeExceptionally(t)
        return@launch
      }
      for (dependency in scope.dependencies()) {
        // attach the current fixture scope (dependent) as a child of dependency scope
        // => dependency fixture scope will wait for the current scope to complete
        // => this ensures the correct tear-down order: dependents are torn down before dependencies.
        attachAsChildTo(dependency)
      }
      val fixtureScope = childScope("Fixture '$debugString'")
      deferred.complete(ScopedValue(fixture, fixtureScope))
      try {
        awaitCancellation()
      }
      finally {
        withContext(NonCancellable) {
          fixtureScope.coroutineContext.job.join()
          tearDown()
        }
      }
    }
    return deferred
  }
}


private typealias ScopedValue<T> = Pair<T, CoroutineScope>

private class TestFixtureInitializerReceiverImpl<T>(
  private val testScope: CoroutineScope,
  private val context: TestContext,
) : TestFixtureInitializer.R<T> {

  /**
   * @return collection of fixture scopes, which should wait for the completion of this scope before own tear down
   */
  private val _dependencies = LinkedHashSet<CoroutineScope>()

  override suspend fun <T> TestFixture<T>.init(): T {
    val (fixture, fixtureScope) = (this as TestFixtureImpl<T>).init(testScope, context).await()
    _dependencies.add(fixtureScope)
    return fixture
  }

  override fun initialized(fixture: T, tearDown: suspend () -> Unit): TestFixtureInitializer.InitializedTestFixture<T> {
    return InitializedTestFixtureData(fixture, tearDown)
  }

  fun dependencies(): Set<CoroutineScope> {
    return _dependencies
  }
}

private data class InitializedTestFixtureData<T>(
  val fixture: T,
  val tearDown: suspend () -> Unit,
) : TestFixtureInitializer.InitializedTestFixture<T>
