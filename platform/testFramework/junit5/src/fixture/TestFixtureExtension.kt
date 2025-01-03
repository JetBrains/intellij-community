// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.fixture

import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.support.HierarchyTraversalMode
import org.junit.platform.commons.support.ReflectionSupport
import java.lang.reflect.Modifier
import java.util.function.Predicate
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class TestFixtureExtension : BeforeAllCallback,
                                      BeforeEachCallback,
                                      AfterEachCallback,
                                      AfterAllCallback {

  override fun beforeAll(context: ExtensionContext) {
    before(context, static = true)
  }

  override fun beforeEach(context: ExtensionContext) {
    before(context, static = false)
  }

  private fun before(context: ExtensionContext, static: Boolean) {
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
      pendingFixtures.add(fixture.init(testScope, context.uniqueId))
    }
    awaitFixtureInitialization(testScope, pendingFixtures)
    context.getStore(ExtensionContext.Namespace.GLOBAL).put("TestFixtureExtension", testScope)
  }

  override fun afterEach(context: ExtensionContext) {
    after(context)
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