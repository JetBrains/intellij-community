// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.storage

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.frame.FrontendXExecutionStack
import com.intellij.platform.debugger.impl.rpc.XExecutionStackDto
import com.intellij.platform.debugger.impl.rpc.XExecutionStackId
import com.intellij.platform.util.coroutines.childScope
import fleet.rpc.core.RpcFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicInteger

internal class FrontendXExecutionStacksStorageTest {
  @Test
  fun `same id gives the same stack`() = withStorageScope { scope ->
    val first = scope.getOrCreateExecutionStack(stackDto(1), project)
    val second = scope.getOrCreateExecutionStack(stackDto(1), project)
    assertSame(first, second)
  }

  @Test
  fun `different ids give different stacks`() = withStorageScope { scope ->
    val first = scope.getOrCreateExecutionStack(stackDto(1), project)
    val second = scope.getOrCreateExecutionStack(stackDto(2), project)
    assertNotSame(first, second)
  }

  @Test
  fun `factory runs once per id`() = withStorageScope { scope ->
    val storage = FrontendXExecutionStacksStorage()
    val created = AtomicInteger()
    val create = {
      created.incrementAndGet()
      FrontendXExecutionStack(stackDto(1), project, scope)
    }
    storage.getOrCreateExecutionStack(XExecutionStackId(1), create)
    storage.getOrCreateExecutionStack(XExecutionStackId(1), create)
    storage.getOrCreateExecutionStack(XExecutionStackId(2), create)
    assertEquals(2, created.get())
  }
}

private val project: Project = proxy { name -> error("Project.$name is not expected") }

private fun withStorageScope(test: (CoroutineScope) -> Unit) = runBlocking {
  val scope = childScope("FrontendXExecutionStacksStorageTest", FrontendXExecutionStacksStorage())
  try {
    test(scope)
  }
  finally {
    scope.cancel()
  }
}

private fun stackDto(id: Int) = XExecutionStackDto(
  executionStackId = XExecutionStackId(id),
  displayName = "thread $id",
  icon = null,
  iconFlow = RpcFlow.empty(),
  descriptor = null,
  topFrame = CompletableDeferred(),
)

private inline fun <reified T> proxy(crossinline methodResult: (String) -> Any?): T {
  return Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { instance, method, arguments ->
    when (method.name) {
      "equals" -> instance === arguments?.firstOrNull()
      "hashCode" -> System.identityHashCode(instance)
      "toString" -> "Test proxy for ${T::class.java.name}"
      else -> methodResult(method.name)
    }
  } as T
}
