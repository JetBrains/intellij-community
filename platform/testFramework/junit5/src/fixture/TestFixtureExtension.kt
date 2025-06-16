// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.eel.EelApi
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider.Companion.getEelForParametrizedTestProvider
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
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
  }

  override fun beforeEach(context: ExtensionContext) {
    // If test is parametrized with eel -- postpone fixture initialization till interception: we can't call it before it:
    // we need invocation context.
    if (context.getEelForParametrizedTestProvider() == null) {
      before(context, static = false)
    }
  }

  override fun interceptTestTemplateMethod(invocation: InvocationInterceptor.Invocation<Void>, invocationContext: ReflectiveInvocationContext<Method>, extensionContext: ExtensionContext) {
    val eelForFixturesProvider = extensionContext.getEelForParametrizedTestProvider()
    if (eelForFixturesProvider != null) {
      before(extensionContext, static = false, eelApi = eelForFixturesProvider.getEel(invocationContext))
    }

    super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)

    if (eelForFixturesProvider != null) {
      after(extensionContext)
    }
  }

  private fun before(context: ExtensionContext, static: Boolean, eelApi: EelApi? = null) {
    val testClass: Class<*> = context.testClass.getOrNull() ?: return
    val testInstance = context.testInstance.getOrNull()

    @OptIn(DelicateCoroutinesApi::class)
    val testScope = GlobalScope.childScope(context.displayName)
    val pendingFixtures = ArrayList<Deferred<*>>()
    val fields = ReflectionSupport.findFields(testClass, Predicate { field ->
      TestFixture::class.java.isAssignableFrom(field.type) && Modifier.isStatic(field.modifiers) == static
    }, HierarchyTraversalMode.TOP_DOWN)
    for (field in fields) {
      field.isAccessible = true
      val fixture = field.get(testInstance) as TestFixtureImpl<*>
      pendingFixtures.add(fixture.init(testScope, TestContextImpl(context, eelApi)))
    }
    awaitFixtureInitialization(testScope, pendingFixtures)
    context.getStore(ExtensionContext.Namespace.GLOBAL).put("TestFixtureExtension", testScope)
  }

  override fun afterEach(context: ExtensionContext) {
    if (context.getEelForParametrizedTestProvider() == null) {
      after(context)
    }
  }

  override fun afterAll(context: ExtensionContext) {
    after(context)
  }

  private fun after(context: ExtensionContext) {
    val testScope = context.getStore(ExtensionContext.Namespace.GLOBAL).get("TestFixtureExtension") ?: return
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