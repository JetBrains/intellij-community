// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.junit5.TestDisposable
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.junit.platform.commons.util.AnnotationUtils.findAnnotatedFields
import org.junit.platform.commons.util.ReflectionUtils

@TestOnly
internal class TestDisposableExtension
  : BeforeEachCallback,
    ParameterResolver {

  override fun beforeEach(context: ExtensionContext) {
    val instance = context.requiredTestInstance
    for (field in findAnnotatedFields(instance.javaClass, TestDisposable::class.java, ReflectionUtils::isNotStatic)) {
      ReflectionUtils.makeAccessible(field)[instance] = context.testDisposable()
    }
  }

  override fun supportsParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Boolean {
    return parameterContext.parameter.type === Disposable::class.java && parameterContext.isAnnotated(TestDisposable::class.java)
  }

  override fun resolveParameter(parameterContext: ParameterContext, extensionContext: ExtensionContext): Any {
    return extensionContext.testDisposable()
  }
}

private fun ExtensionContext.testDisposable(): Disposable {
  return getStore(ExtensionContext.Namespace.GLOBAL)
    .computeIfAbsent("test disposable") {
      DisposableResource(Disposer.newCheckedDisposable(uniqueId))
    }
    .disposable
}

private class DisposableResource(val disposable: CheckedDisposable) : ExtensionContext.Store.CloseableResource {

  override fun close() {
    Assertions.assertFalse(disposable.isDisposed)
    Disposer.dispose(disposable)
  }
}
