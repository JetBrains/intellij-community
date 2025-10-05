// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.eel.EelApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider.Companion.getEelForParametrizedTestProvider
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class TestFixtureExtension : BeforeAllCallback,
                                      BeforeEachCallback,
                                      AfterEachCallback,
                                      AfterAllCallback,
                                      InvocationInterceptor {

  override fun beforeAll(context: ExtensionContext) {
    before(context, static = true)
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      before(context, static = false)
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      return
    }
    // If the test is parametrized with eel -- postpone fixture initialization till interception: we can't call it before it:
    // we need invocation context.
    if (context.getEelForParametrizedTestProvider() == null) {
      before(context, static = false)
    }
  }

  override fun <T : Any?> interceptTestClassConstructor(invocation: InvocationInterceptor.Invocation<T?>?, invocationContext: ReflectiveInvocationContext<Constructor<T>>, extensionContext: ExtensionContext): T? {
    val instance = super.interceptTestClassConstructor(invocation, invocationContext, extensionContext)
    val eelForFixturesProvider = extensionContext.getEelForParametrizedTestProvider()
    if (eelForFixturesProvider != null) {
      before(extensionContext, static = false, eelApi = eelForFixturesProvider.getEel(invocationContext), instance=instance)

    }
    return instance
  }

  override fun interceptTestTemplateMethod(invocation: InvocationInterceptor.Invocation<Void>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    val eelForFixturesProvider = extensionContext.getEelForParametrizedTestProvider()
    if (eelForFixturesProvider != null) {
      before(extensionContext, static = false, eelApi = eelForFixturesProvider.getEel(invocationContext))
    }

    super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)
  }

  private fun collectTestInstances(context: ExtensionContext): Map<Class<*>, Any> {
    val allTestInstances = context.testInstances.getOrNull()?.allInstances.orEmpty()
    return allTestInstances.associateBy { it.javaClass }
  }

  private fun before(context: ExtensionContext, static: Boolean, eelApi: EelApi? = null, instance: Any? = null) {
    val testClass: Class<*> = context.testClass.getOrNull() ?: return

    @OptIn(DelicateCoroutinesApi::class)
    val testScope = GlobalScope.childScope(context.displayName)
    val pendingFixtures = ArrayList<Deferred<*>>()

    val classToTestInstance = collectTestInstances(context)
    // start with the outermost one, in case the inner ones depend on them
    val classes = context.enclosingTestClasses + listOf(testClass)
    for (clazz in classes) {
      val testInstance = classToTestInstance[clazz] ?: instance
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
    context.getStore(ExtensionContext.Namespace.GLOBAL).put("TestFixtureExtension_$static", testScope)
  }

  override fun afterEach(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      return
    }
    after(context, static = false)
  }

  override fun afterAll(context: ExtensionContext) {
    if (context.testInstanceLifecycle.getOrNull() == TestInstance.Lifecycle.PER_CLASS) {
      after(context, static = false)
    }
    after(context, static = true)
  }

  private fun after(context: ExtensionContext, static: Boolean) {
    val testScope = context.getStore(ExtensionContext.Namespace.GLOBAL).get("TestFixtureExtension_$static") ?: return
    @Suppress("SSBasedInspection")
    runBlocking {
      (testScope as CoroutineScope).coroutineContext.job.cancelAndJoin()
    }
  }
}

private fun awaitFixtureInitialization(cleanupScope: CoroutineScope, pendingFixtures: List<Deferred<*>>) {
  @Suppress("SSBasedInspection")
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