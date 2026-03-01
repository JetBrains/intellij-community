package com.intellij.remoteDev.tests

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.remoteDev.tests.impl.utils.runLogged
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.function.IntFunction
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
@ApiStatus.Internal
interface LambdaIdeContext : CoroutineScope {
  var testData: Any?
  val testFixtures: TestFixtures
  val globalDisposable: Disposable
  fun addAfterEachCleanup(action: () -> Unit)
}

@ApiStatus.Internal
class TestFixtures(private val fixtures: MutableList<Any> = mutableListOf()) : MutableList<Any> by fixtures {
  inline fun <reified T> get(): List<T> = this.filterIsInstance<T>()
  inline fun <reified T> first(): T = this.filterIsInstance<T>().first()

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any?> toArray(generator: IntFunction<Array<out T?>?>): Array<out T?> {
    return (fixtures as java.util.Collection<Any>).toArray(generator)
  }
}

@ApiStatus.Internal
interface LambdaMonolithContext : LambdaBackendContext, LambdaFrontendContext

@ApiStatus.Internal
interface LambdaBackendContext : LambdaIdeContext

@ApiStatus.Internal
interface LambdaFrontendContext : LambdaIdeContext

abstract class LambdaIdeContextClass(
  override var testData: Any? = null,
  override var testFixtures: TestFixtures = TestFixtures(mutableListOf()),
  override val globalDisposable: Disposable = Disposer.newDisposable("Global LambdaIdeContext disposable"),
) : LambdaIdeContext {
  private val actionsAfterEach: MutableList<() -> Unit> = mutableListOf()

  /**
   * Added action will be executed after the current test finishes
   */
  override fun addAfterEachCleanup(action: () -> Unit) {
    actionsAfterEach.add(action)
  }

  @TestOnly
  internal suspend fun runAfterEachCleanup() {
    if (actionsAfterEach.isNotEmpty()) {
      actionsAfterEach.reversed().forEachIndexed { index, function ->
        runLogged("After each cleanup action [#$index/${actionsAfterEach.size}]") {
          function.invoke()
        }
      }
    }

    runLogged("Disposing global disposable", 10.seconds) {
      Disposer.dispose(globalDisposable)
    }

    testFixtures.clear()
  }
}

@ApiStatus.Internal
class LambdaBackendContextClass(override val coroutineContext: CoroutineContext) :
  LambdaIdeContextClass(), LambdaBackendContext

@ApiStatus.Internal
class LambdaFrontendContextClass(override val coroutineContext: CoroutineContext) :
  LambdaIdeContextClass(), LambdaFrontendContext

@ApiStatus.Internal
class LambdaMonolithContextClass(override val coroutineContext: CoroutineContext) :
  LambdaIdeContextClass(), LambdaMonolithContext