// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelSource
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.impl.providers.getIjentTestProviders
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider.Companion.makeFixturesEelAware
import com.intellij.testFramework.junit5.impl.TypedStoreKey
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.getTyped
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.util.AnnotationUtils.findAnnotation
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class EelInterceptor : InvocationInterceptor, BeforeAllCallback, BeforeEachCallback, AfterEachCallback {
  private companion object {
    const val PROVIDER_PROP_NAME = "java.nio.file.spi.DefaultFileSystemProvider"
    val ExtensionContext.atLeastOneRemoteEelRequired: Boolean
      get() =
        OS.current() !in findAnnotation(testClass.get(), TestApplicationWithEel::class.java).get().osesMayNotHaveRemoteEels

    val ReflectiveInvocationContext<Method>.eelHolderArgs: List<EelHolder>
      get() =
        arguments.filterIsInstance<EelHolder>()

    val eelForFixturesProvider = EelForFixturesProvider { invocationContext ->
      invocationContext.eelHolderArgs.first().eel
    }

    /**
     * When created by parametrized class ctor or parametrized test, saved in ext. context to be closed later
     */
    private val KEY_FOR_MANAGER: TypedStoreKey<EelsManager> = TypedStoreKey.createKey()
  }

  override fun beforeEach(context: ExtensionContext) {
    val testMethod = context.testMethod.getOrNull() ?: return
    if (testMethod.annotations.filterIsInstance<EelSource>().any() ||
        testMethod.parameters.any { it.annotations.filterIsInstance<EelSource>().any() }
    ) {
      context.makeFixturesEelAware(eelForFixturesProvider)
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    //    -Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
    val name = MultiRoutingFileSystemProvider::class.qualifiedName!!
    assert(System.getProperty(PROVIDER_PROP_NAME) == name) {
      "Please add `-D$PROVIDER_PROP_NAME=$name` as VMOption as eel needs custom nio provider"
    }
    if (context.atLeastOneRemoteEelRequired) {
      assert(getIjentTestProviders().isNotEmpty()) {
        """
          No remote (ijent-based) eel implementation was found on class-path. 
          You have 2 options:
          1. Add your OS to ${TestApplicationWithEel::osesMayNotHaveRemoteEels} and stick with the local eel only (not recommended).
          2. Make sure at least one ${EelIjentTestProvider::class} exists on the class-path.
        """.trimIndent()
      }
    }
  }

  override fun <T : Any> interceptTestClassConstructor(invocation: InvocationInterceptor.Invocation<T>, invocationContext: ReflectiveInvocationContext<Constructor<T>>, extensionContext: ExtensionContext): T {
    createEelManager(invocationContext, extensionContext)
    return super.interceptTestClassConstructor(invocation, invocationContext, extensionContext)
  }

  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    createEelManager(invocationContext, extensionContext)
    super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)
  }

  override fun afterEach(context: ExtensionContext) {
    context.store.getTyped(KEY_FOR_MANAGER)?.close()
  }

  private fun createEelManager(
    invocationContext: ReflectiveInvocationContext<*>,
    extensionContext: ExtensionContext,
  ) {
    val eelManager = EelsManager.create(invocationContext, extensionContext) ?: return
    extensionContext.makeFixturesEelAware(eelForFixturesProvider)
    extensionContext.store.put(KEY_FOR_MANAGER, eelManager)
  }
}
