// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.eel.EelApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider.Companion.getEelForParametrizedTestProvider
import com.intellij.testFramework.junit5.impl.TypedStoreKey
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.computeIfAbsent
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.remove
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class TestFixtureExtension : BeforeAllCallback,
                                      BeforeEachCallback,
                                      AfterEachCallback,
                                      AfterAllCallback,
                                      InvocationInterceptor {
  private companion object {
    /**
     * Eels resolved while test class instances were being constructed (a `@ParameterizedClass` taking an
     * `EelHolder` constructor parameter).
     *
     * The only [ExtensionContext] available at construction time is the *class* one, whose store is shared by every
     * class-template invocation and which is never paired with a matching `after` callback. So no [CoroutineScope]
     * is created there; the eel is merely remembered, and [beforeEach]/[beforeAll] do the actual initialization on a
     * context that [afterEach]/[afterAll] also see.
     */
    val ctorEelsKey = TypedStoreKey.createKey<CtorEels>()
  }

  override fun beforeAll(context: ExtensionContext) {
    before(context, static = true)
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      // The instance already exists here: ClassBasedTestDescriptor.before() creates it before invoking
      // BeforeAllCallbacks, so an eel supplied through the constructor is available.
      before(context, static = false, eelApi = context.peekCtorEel()?.eel)
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      return
    }
    val ctorEel = context.peekCtorEel()
    if (ctorEel != null) {
      // Parametrized class: the eel is already known, so initialize here rather than during interception.
      before(context, static = false, eelApi = ctorEel.eel)
      return
    }
    // Parametrized test method: postpone fixture initialization till interception, we need the invocation context.
    if (context.getEelForParametrizedTestProvider() == null) {
      before(context, static = false)
    }
  }

  override fun <T : Any> interceptTestClassConstructor(
    invocation: InvocationInterceptor.Invocation<T>,
    invocationContext: ReflectiveInvocationContext<Constructor<T>>,
    extensionContext: ExtensionContext,
  ): T {
    val instance = super.interceptTestClassConstructor(invocation, invocationContext, extensionContext)
    val eelForFixturesProvider = extensionContext.getEelForParametrizedTestProvider()
    if (eelForFixturesProvider != null) {
      extensionContext.computeIfAbsent(ctorEelsKey) { CtorEels() }[instance] = CtorEel(eelForFixturesProvider.getEel(invocationContext))
    }
    return instance
  }

  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void?>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    val eelForFixturesProvider = extensionContext.getEelForParametrizedTestProvider()
    // Do not initialize again when beforeEach already did it for a parametrized class: the fixtures would be handed
    // back without attaching to this scope, so it would stay empty and merely shadow the real one in the store chain.
    if (eelForFixturesProvider != null && extensionContext.peekCtorEel() == null) {
      before(extensionContext, static = false, eelApi = eelForFixturesProvider.getEel(invocationContext))
    }

    super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)
  }

  private fun collectTestInstances(context: ExtensionContext): Map<Class<*>, Any> =
    context.allInstances.associateBy { it.javaClass }

  private fun ExtensionContext.peekCtorEel(): CtorEel? =
    this[ctorEelsKey]?.find(allInstances)

  private fun ExtensionContext.forgetCtorEel() {
    this[ctorEelsKey]?.forget(allInstances)
  }

  private val ExtensionContext.allInstances get() = testInstances.getOrNull()?.allInstances.orEmpty()

  private fun before(context: ExtensionContext, static: Boolean, eelApi: EelApi? = null) {
    val testClass: Class<*> = context.testClass.getOrNull() ?: return
    if (static && !context.enclosingTestClasses.isEmpty()) {
      // There can't be static fixtures in nested classes
      return
    }

    TestLoggerFactory.fixtureInitialization<Exception>(static, context.displayName) {
      // The failure belongs to the test or the class that owns the fixture, so `after` of the same context reports it.
      // A rethrow here would also hand it to the uncaught exception handler of the run, which the next test drains:
      // the failed teardown of a class fixture then also failed the first test of the next class.
      val failure = FixtureFailure()
      val exceptionHandler = CoroutineExceptionHandler { _, exception -> failure.record(exception) }

      @OptIn(DelicateCoroutinesApi::class)
      val testScope = GlobalScope.childScope(context.displayName + "[" + context.uniqueId + "]", exceptionHandler)
      val store = context.getStore(ExtensionContext.Namespace.GLOBAL)
      store.put("TestFixtureExtension_$static", testScope)
      store.put("TestFixtureExtension_failure_$static", failure)
      val pendingFixtures = ArrayList<Deferred<*>>()

      val classToTestInstance = collectTestInstances(context)
      // start with the outermost one, in case the inner ones depend on them
      val classes = context.enclosingTestClasses + listOf(testClass)
      for (clazz in classes) {
        val testInstance = classToTestInstance[clazz]
        val fieldsForDeclaringClass = ReflectionSupport.findFields(clazz, Predicate { field ->
          TestFixture::class.java.isAssignableFrom(field.type) && Modifier.isStatic(field.modifiers) == static
        }, HierarchyTraversalMode.TOP_DOWN)

        for (field in fieldsForDeclaringClass) {
          field.isAccessible = true
          val fixture = field.get(testInstance) as TestFixtureImpl<*>
          pendingFixtures.add(fixture.init(testScope, TestContextImpl(context, eelApi)))
        }
      }

      awaitFixtureInitialization(testScope, pendingFixtures)
    }
  }

  override fun afterEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      return
    }
    val failure = try {
      after(context, static = false)
    }
    finally {
      context.forgetCtorEel()
    }
    failure?.let { throw it }
  }

  override fun afterAll(context: ExtensionContext) {
    var failure: Throwable? = null
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      failure = try {
        after(context, static = false)
      }
      finally {
        context.forgetCtorEel()
      }
    }
    val staticFailure = after(context, static = true)
    if (failure == null) {
      failure = staticFailure
    }
    else if (staticFailure != null) {
      failure.addSuppressed(staticFailure)
    }
    failure?.let { throw it }
  }

  /** Tears down the fixtures of [context] and answers the failure of a fixture, which fails the test or the class. */
  private fun after(context: ExtensionContext, static: Boolean): Throwable? {
    if (static && !context.enclosingTestClasses.isEmpty()) {
      // There can't be static fixtures in nested classes
      return null
    }

    TestLoggerFactory.onFixturesDisposeStart(static)

    // `remove` is local to this context's store, whereas `get` walks up the parent stores. Using it enforces that
    // `before` and `after` always run on the very same context: a scope stored on a container context can no longer
    // be cancelled from a test context, nor the other way round.
    val store = context.getStore(ExtensionContext.Namespace.GLOBAL)
    val testScope = store.remove("TestFixtureExtension_$static") ?: return null
    runBlocking {
      (testScope as CoroutineScope).coroutineContext.job.cancelAndJoin()
    }
    return (store.remove("TestFixtureExtension_failure_$static") as FixtureFailure?)?.first
  }
}

private fun awaitFixtureInitialization(cleanupScope: CoroutineScope, pendingFixtures: List<Deferred<*>>) {
  runBlocking {
    try {
      pendingFixtures.awaitAll()
    }
    catch (e: Throwable) {
      try {
        cleanupScope.coroutineContext.job.cancelAndJoin()
      }
      catch (exceptionDuringCleanup: Throwable) {
        e.addSuppressed(Throwable("Exception during cleanup of test fixture", exceptionDuringCleanup))
      }
      throw e
    }
  }
}

/**
 * The first failure of the fixtures of one context. A later failure is a suppressed exception of the first one, so the
 * report of the test or the class keeps all of them.
 */
private class FixtureFailure {
  private val recorded = AtomicReference<Throwable?>()

  val first: Throwable?
    get() = recorded.get()

  fun record(exception: Throwable) {
    if (!recorded.compareAndSet(null, exception)) {
      val first = recorded.get()
      if (first !== exception) {
        first?.addSuppressed(exception)
      }
    }
  }
}

/**
 * Eel a test class instance was constructed with, or `null` when the instance was constructed without one.
 *
 * The distinction between "no eel" and "no entry at all" matters: the presence of an entry is what tells
 * [TestFixtureExtension] that the instance comes from an eel-parametrized class, and that fixture initialization
 * must therefore not be postponed until the test method is intercepted.
 */
private class CtorEel(val eel: EelApi?)

/**
 * Eels remembered per test class instance, by identity.
 *
 * Instances are recorded while the test class is being constructed and dropped once the corresponding test is over.
 * A single registry is shared by all invocations of one class template, hence the identity map: it is keyed by the
 * instance rather than by the extension context, which at construction time is the class-wide one.
 */
private class CtorEels {

  private val byInstance: MutableMap<Any, CtorEel> = Collections.synchronizedMap(IdentityHashMap())

  operator fun set(instance: Any, ctorEel: CtorEel) {
    byInstance[instance] = ctorEel
  }

  /**
   * Looks up [instances] (outermost first, as [org.junit.jupiter.api.extension.TestInstances.getAllInstances] returns
   * them), preferring an instance that actually carries an eel: a `@Nested` class is constructed without one, while
   * its enclosing parametrized class is not.
   */
  fun find(instances: List<Any>): CtorEel? {
    val found = instances.mapNotNull { byInstance[it] }
    return found.firstOrNull { it.eel != null } ?: found.firstOrNull()
  }

  fun forget(instances: List<Any>) {
    for (instance in instances) {
      byInstance.remove(instance)
    }
  }
}
