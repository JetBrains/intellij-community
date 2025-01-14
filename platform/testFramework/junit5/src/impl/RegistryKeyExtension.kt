// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.RegistryKey
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.platform.commons.support.AnnotationSupport

@TestOnly
internal class RegistryKeyExtension : AbstractInvocationInterceptor() {

  override fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, context: ExtensionContext): T {
    val annotations = AnnotationSupport.findRepeatableAnnotations(context.testClass, RegistryKey::class.java) +
                      AnnotationSupport.findRepeatableAnnotations(context.element, RegistryKey::class.java)
    if (annotations.isEmpty()) {
      return invocation.proceed()
    }
    val valuesBefore = annotations.map { annotation ->
      Pair(annotation, annotation.setValue())
    }
    try {
      return invocation.proceed()
    }
    finally {
      for ((annotation, previousValue) in valuesBefore.asReversed()) {
        annotation.resetValue(previousValue)
      }
    }
  }

  private fun RegistryKey.setValue(): String? = Registry.Companion.get(key).run {
    val previousValue = Registry.Companion.stringValue(key)
    setValue(value)
    previousValue
  }

  private fun RegistryKey.resetValue(previousValue: String?): Unit = Registry.Companion.get(key).run {
    if (previousValue == null) {
      resetToDefault()
    }
    else {
      setValue(previousValue)
    }
  }
}