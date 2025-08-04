// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.testFramework.junit5.eel.params.impl.junit5

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolderImpl
import com.intellij.platform.testFramework.junit5.eel.params.api.LocalEelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider.StartResult.Skipped
import com.intellij.platform.testFramework.junit5.eel.params.spi.EelIjentTestProvider.StartResult.Started
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.*
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.util.AnnotationUtils
import org.opentest4j.TestAbortedException
import java.io.Closeable

/**
 * Call [create] to get manager that looks for [EelHolder] in invocation context and "activate" it.
 * [close] is used to destroy it.
 */
internal class EelsManager private constructor(private val eelHolders: List<EelHolder>, extensionContext: ExtensionContext) : java.lang.AutoCloseable {
  // Autocloseable things might be closed by JUnit, hence need not be closed two times
  private var closed = false
  private val closeAfterTest: MutableList<Closeable> = mutableListOf<Closeable>()
  private val scope: CoroutineScope = ApplicationManager.getApplication().service<EelTestService>().scope.childScope("Eel test child scope")

  companion object {
    fun create(invocationContext: ReflectiveInvocationContext<*>, extensionContext: ExtensionContext): EelsManager? {
      val eelHolders = invocationContext.eelHolderArgs
      if (eelHolders.isEmpty()) return null
      return EelsManager(eelHolders, extensionContext)
    }
  }

  init {
    val testContext = extensionContext.parent.get()
    testContext.getStore(ExtensionContext.Namespace.GLOBAL).put("remoteEelCallback", object : AutoCloseable {
      override fun close() {
        if (testContext.store.get(REMOTE_EEL_EXECUTED) == null && extensionContext.atLeastOneRemoteEelRequired) {

          val advice = buildString {
            if (com.intellij.util.system.OS.CURRENT == com.intellij.util.system.OS.Windows) {
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
            val (eel, target, closable) = eelHolder.startIjentProvider(scope)
            closable?.let {
              closeAfterTest.add(it)
            }
            eelHolder.eel = eel
            eelHolder.target = target
            testContext.store.put(REMOTE_EEL_EXECUTED, true)
          }
          LocalEelHolder -> Unit
        }
      }
    }
  }

  override fun close() {
    if (closed) return
    closed = true
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
      if (annotation != null && provider.isMandatory(annotation)) {
        throw IllegalStateException(
          "Test is marked with mandatory annotation $annotation but $this is not available: $skippedReason")
      }
      else {
        throw TestAbortedException(skippedReason)
      }
    }
  }
}

internal val ReflectiveInvocationContext<*>.eelHolderArgs: List<EelHolder>
  get() =
    arguments.filterIsInstance<EelHolder>()

@Service
private class EelTestService(val scope: CoroutineScope)

internal val ExtensionContext.store: ExtensionContext.Store get() = getStore(ExtensionContext.Namespace.GLOBAL)
private const val REMOTE_EEL_EXECUTED = "REMOTE_EEL_EXECUTED"

private val ExtensionContext.atLeastOneRemoteEelRequired: Boolean
  get() =
    OS.current() !in AnnotationUtils.findAnnotation(testClass.get(), TestApplicationWithEel::class.java).get().osesMayNotHaveRemoteEels
