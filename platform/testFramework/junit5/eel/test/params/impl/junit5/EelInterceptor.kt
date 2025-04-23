// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ijent.IjentApi
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.impl.providers.LocalEelTestProvider
import com.intellij.platform.testFramework.junit5.eel.params.impl.providers.getEelTestProviders
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelTestProvider
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelTestProvider.StartResult.Skipped
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelTestProvider.StartResult.Started
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.util.AnnotationUtils
import org.opentest4j.TestAbortedException
import java.io.Closeable
import java.lang.reflect.Method

@TestOnly
internal class EelInterceptor : InvocationInterceptor, BeforeAllCallback {
  private companion object {
    const val REMOTE_EEL_EXECUTED = "REMOTE_EEL_EXECUTED"

    val ExtensionContext.atLeastOneRemoteEelRequired: Boolean
      get() =
        AnnotationUtils.findAnnotation(testClass.get(), TestApplicationWithEel::class.java).get().atLeastOneRemoteEelRequired

    val ExtensionContext.store: ExtensionContext.Store get() = getStore(ExtensionContext.Namespace.GLOBAL)
  }

  override fun beforeAll(context: ExtensionContext) {
    if (context.atLeastOneRemoteEelRequired) {
      assert(getEelTestProviders().any { it !is LocalEelTestProvider }) {
        """
          No remote (ijent-based) eel implementation was found on class-path. 
          You have 2 options:
          1. Disable ${TestApplicationWithEel::atLeastOneRemoteEelRequired} and stick with the local eel only (not recommended).
          2. Make sure at least one ${EelTestProvider::class} exists on the class-path.
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
    val annotations = extensionContext.element.get().annotations + extensionContext.testClass.get().annotations
    val eelHolders = invocationContext.arguments.filterIsInstance<EelHolderImpl>()


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
            If that was your plan, set ${TestApplicationWithEel::atLeastOneRemoteEelRequired} and stick with the local eel only (not recommended).
            But much better to do the following: $advice
            Testing something against local eel only is not recommended. 
          """.trimIndent())
        }
      }
    })



    runBlocking {
      for (eelHolderImpl in eelHolders) {
        val (eel, closable) = eelHolderImpl.eelTestProvider.startIjentProvider(scope, annotations)
        closable?.let {
          closeAfterTest.add(it)
        }
        eelHolderImpl.eel = eel
        testContext.store.put(REMOTE_EEL_EXECUTED, true)
      }
    }


    try {
      super.interceptTestTemplateMethod(invocation, invocationContext, extensionContext)
    }
    finally {
      runBlocking {
        for (arg in eelHolders) {
          (arg.eel as? IjentApi)?.apply {
            close()
            waitUntilExit()
          }
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
private suspend fun <T : Any> EelTestProvider<T>.startIjentProvider(
  scope: CoroutineScope,
  annotations: Array<Annotation>,
): Started {
  val mandatoryAnnotation = mandatoryAnnotationClass?.let { annotationClass ->
    annotations.filterIsInstance(annotationClass.java).firstOrNull()
  }
  when (val r = start(scope, mandatoryAnnotation)) {
    is Started -> {
      return r
    }
    is Skipped -> {
      val skippedReason = r.skippedReason
      if (mandatoryAnnotation != null) {
        throw IllegalStateException("Test is marked with mandatory annotation $mandatoryAnnotation but $this is not available: $skippedReason")
      }
      else {
        throw TestAbortedException(skippedReason)
      }
    }
  }
}
