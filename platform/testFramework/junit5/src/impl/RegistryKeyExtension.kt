// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.RegistryKeyAppLevel
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.get
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.platform.commons.support.AnnotationSupport
import kotlin.jvm.optionals.getOrNull

@TestOnly
internal class RegistryKeyExtension : AbstractInvocationInterceptor(), BeforeAllCallback, AfterAllCallback {
  private data class RegistryValueHolder(val value: RegistryValue, val oldValue: String)
  companion object {
    private val typedKey = TypedStoreKey(RegistryValueHolder::class.java)
  }

  override fun beforeAll(context: ExtensionContext) {
    val annotation = AnnotationSupport.findAnnotation(context.testClass, RegistryKeyAppLevel::class.java).getOrNull()
    if (annotation != null) {
      val registryValue = Registry.get(annotation.key)
      val oldValue = registryValue.asString()
      context[typedKey] = RegistryValueHolder(registryValue, oldValue)
      registryValue.setValue(annotation.value)
    }
  }

  override fun afterAll(context: ExtensionContext) {
    val regValue = context[typedKey] ?: return
    regValue.value.setValue(regValue.oldValue)
  }

  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    val annotations = AnnotationSupport.findRepeatableAnnotations(context.testClass, RegistryKey::class.java) +
                      AnnotationSupport.findRepeatableAnnotations(context.element, RegistryKey::class.java)
    if (annotations.isEmpty()) {
      return invocation.proceed()
    }
    val valuesBefore = annotations.map { annotation ->
      setRegistryValue(annotation)
    }
    try {
      return invocation.proceed()
    }
    finally {
      for (handle in valuesBefore.asReversed()) {
        handle()
      }
    }
  }

  /**
   * @return resetting handle
   */
  private fun setRegistryValue(keyValue: RegistryKey): () -> Unit {
    val rv: RegistryValue = Registry.get(keyValue.key)
    val previousValue = rv.asString()
    rv.setValue(keyValue.value)
    return {
      rv.setValue(previousValue)
    }
  }
}
