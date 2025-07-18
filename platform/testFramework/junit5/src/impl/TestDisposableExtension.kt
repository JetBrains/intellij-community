// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.computeIfAbsent
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
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
    val disposable = context[testDisposableKey] ?: return
    Assertions.assertFalse(disposable.isDisposed)
    Disposer.dispose(disposable)
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type === Disposable::class.java && parameterContext.isAnnotated(TestDisposable::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return extensionContext.testDisposable()
  }
}

private val testDisposableKey = TypedStoreKey.createKey<CheckedDisposable>()

private fun ExtensionContext.testDisposable(): CheckedDisposable {
  return computeIfAbsent(testDisposableKey) {
      Disposer.newCheckedDisposable(uniqueId)
    }
}
