// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.*
import org.junit.platform.commons.util.AnnotationUtils.findAnnotatedFields
import org.junit.platform.commons.util.ReflectionUtils

@TestOnly
internal class TestDisposableExtension
  : BeforeEachCallback,
    AfterEachCallback,
    ParameterResolver {

  override fun beforeEach(context: ExtensionContext) {
    val instance = context.requiredTestInstance
    for (field in findAnnotatedFields(instance.javaClass, TestDisposable::class.java, ReflectionUtils::isNotStatic)) {
      ReflectionUtils.makeAccessible(field)[instance] = context.testDisposable()
    }
  }

  override fun afterEach(context: ExtensionContext) {
    context.testDisposableIfRequested()?.let { disposable ->
      Assertions.assertFalse(disposable.isDisposed)
      Disposer.dispose(disposable)
    }
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type === Disposable::class.java && parameterContext.isAnnotated(TestDisposable::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return extensionContext.testDisposable()
  }
}

private const val testDisposableKey = "test disposable"

private fun ExtensionContext.testDisposable(): CheckedDisposable {
  return getStore(ExtensionContext.Namespace.GLOBAL)
    .computeIfAbsent(testDisposableKey) {
      Disposer.newCheckedDisposable(uniqueId)
    }
}

private fun ExtensionContext.testDisposableIfRequested(): CheckedDisposable? {
  return getStore(ExtensionContext.Namespace.GLOBAL)
    .typedGet(testDisposableKey)
}
