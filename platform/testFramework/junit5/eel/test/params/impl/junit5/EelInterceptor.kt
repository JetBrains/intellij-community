// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider
import com.intellij.platform.testFramework.junit5.eel.params.api.*
import com.intellij.platform.testFramework.junit5.eel.params.impl.providers.getIjentTestProviders
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider.StartResult.Skipped
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider.StartResult.Started
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider
import com.intellij.testFramework.junit5.fixture.EelForFixturesProvider.Companion.makeFixturesEelAware
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.util.AnnotationUtils
import org.opentest4j.TestAbortedException
import java.io.Closeable
import java.lang.reflect.Method
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class EelInterceptor : InvocationInterceptor, BeforeAllCallback, BeforeEachCallback {
  private companion object {
    const val REMOTE_EEL_EXECUTED = "REMOTE_EEL_EXECUTED"
    const val PROVIDER_PROP_NAME = "java.nio.file.spi.DefaultFileSystemProvider"

    val ExtensionContext.atLeastOneRemoteEelRequired: Boolean
      get() =
        OS.current() !in AnnotationUtils.findAnnotation(testClass.get(), TestApplicationWithEel::class.java).get().osesMayNotHaveRemoteEels

    val ExtensionContext.store: ExtensionContext.Store get() = getStore(ExtensionContext.Namespace.GLOBAL)
    private val ReflectiveInvocationContext<Method>.eelHolderArgs: List<EelHolder>
      get() =
        arguments.filterIsInstance<EelHolder>()

    private val eelForFixturesProvider = EelForFixturesProvider { invocationContext ->
      invocationContext.eelHolderArgs.first().eel
    }
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


  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    val scope = ApplicationManager.getApplication().service<EelTestService>().scope.childScope("Eel test child scope")
    val closeAfterTest = mutableListOf<Closeable>()
    val eelHolders = invocationContext.eelHolderArgs


    val testContext = extensionContext.parent.get()
    testContext.getStore(ExtensionContext.Namespace.GLOBAL).put("remoteEelCallback", object : ExtensionContext.Store.CloseableResource {
      override fun close() {
        if (testContext.store.get(REMOTE_EEL_EXECUTED) == null && extensionContext.atLeastOneRemoteEelRequired) {

          val advice = buildString {
            if (SystemInfo.isWindows) {
              append("Install WSL2. ")
            }
            append("Install docker. ")
          }

          error("""
            Although some remote (ijent) eel implementations were found on a class-path, all of them were skipped.
            That means, you've tested the local eel implementation only! 
            If that was your plan, configure ${TestApplicationWithEel::osesMayNotHaveRemoteEels} and stick with the local eel only (not recommended).
            But much better to do the following: $advice
            Testing something against local eel only is not recommended. 
          """.trimIndent())
        }
      }
    })



    runBlocking {
      for (eelHolder in eelHolders) {
        when (eelHolder) {
          is EelHolderImpl<*> -> {
            val (eel, closable) = eelHolder.startIjentProvider(scope)
            closable?.let {
              closeAfterTest.add(it)
            }
            eelHolder.eel = eel
            testContext.store.put(REMOTE_EEL_EXECUTED, true)
          }
          LocalEelHolder -> Unit
        }
      }
    }


    try {
      super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)
    }
    finally {
      runBlocking {
        for (arg in eelHolders) {
          when (arg) {
            is EelHolderImpl<*> -> {
              arg.eel.apply {
                close()
                waitUntilExit()
              }
            }
            LocalEelHolder -> Unit
          }
          for (closeable in closeAfterTest) {
            closeable.close()
          }
          scope.coroutineContext.job.cancelAndJoin()
          scope.cancel()
        }
      }
    }


  }

  @Service
  private class EelTestService(val scope: CoroutineScope)


  @TestOnly
  private suspend fun <T : Annotation> EelHolderImpl<T>.startIjentProvider(
    scope: CoroutineScope,
  ): Started {
    val provider = this.eelTestProvider
    when (val r = provider.start(scope, annotation)) {
      is Started -> {
        return r
      }
      is Skipped -> {
        val skippedReason = r.skippedReason
        if (annotation != null) {
          throw IllegalStateException(
            "Test is marked with mandatory annotation $annotation but $this is not available: $skippedReason")
        }
        else {
          throw TestAbortedException(skippedReason)
        }
      }
    }
  }
}
