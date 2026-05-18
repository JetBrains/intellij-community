// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.SystemPropertyClassLevel
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.remove
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@TestOnly
internal class SystemPropertyExtension : InvocationInterceptor, BeforeAllCallback, AfterAllCallback {

  private data class SystemPropertyValueHolder(val propertyKey: String, val previousValue: String?)

  companion object {
    private val classLevelPropertiesKey = TypedStoreKey.createKey<List<SystemPropertyValueHolder>>()
  }

  override fun beforeAll(context: ExtensionContext) {
    val annotations = AnnotationSupport.findRepeatableAnnotations(context.testClass, SystemPropertyClassLevel::class.java)
    if (annotations.isNotEmpty()) {
      context[classLevelPropertiesKey] = annotations.map { annotation ->
        setPropertyValue(annotation.propertyKey, annotation.propertyValue)
      }
    }
  }

  override fun afterAll(context: ExtensionContext) {
    val properties = context.remove(classLevelPropertiesKey) ?: return
    for (property in properties.asReversed()) {
      resetPropertyValue(property)
    }
  }

  override fun interceptBeforeAllMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun <T> interceptTestClassConstructor(
    invocation: InvocationInterceptor.Invocation<T>,
    invocationContext: ReflectiveInvocationContext<Constructor<T>>,
    extensionContext: ExtensionContext,
  ): T {
    return intercept(invocation, invocationContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptTestMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptTestTemplateMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptAfterEachMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptAfterAllMethod(
    invocation: InvocationInterceptor.Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  private fun <T> intercept(invocation: InvocationInterceptor.Invocation<T>, invocationContext: ReflectiveInvocationContext<*>): T {
    val annotations = AnnotationSupport.findRepeatableAnnotations(invocationContext.targetClass, SystemProperty::class.java) +
                      AnnotationSupport.findRepeatableAnnotations(invocationContext.executable, SystemProperty::class.java)
    if (annotations.isEmpty()) {
      return invocation.proceed()
    }
    val valuesBefore = annotations.map { annotation ->
      setPropertyValue(annotation.propertyKey, annotation.propertyValue)
    }
    try {
      return invocation.proceed()
    }
    finally {
      for (property in valuesBefore.asReversed()) {
        resetPropertyValue(property)
      }
    }
  }

  private fun setPropertyValue(propertyKey: String, propertyValue: String): SystemPropertyValueHolder {
    val previousValue = System.getProperty(propertyKey)
    if (propertyValue.isEmpty()) {
      System.clearProperty(propertyKey)
    }
    else {
      System.setProperty(propertyKey, propertyValue)
    }
    return SystemPropertyValueHolder(propertyKey, previousValue)
  }

  private fun resetPropertyValue(property: SystemPropertyValueHolder) {
    if (property.previousValue == null) {
      System.clearProperty(property.propertyKey)
    }
    else {
      System.setProperty(property.propertyKey, property.previousValue)
    }
  }
}
