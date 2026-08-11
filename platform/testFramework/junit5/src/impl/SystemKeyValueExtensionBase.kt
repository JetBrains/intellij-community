// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.remove
import com.intellij.testFramework.junit5.impl.TypedStoreKey.Companion.set
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport
import java.lang.reflect.Constructor
import java.lang.reflect.Method

internal abstract class SystemKeyValueExtensionBase<DATA_HOLDER, ANNO : Annotation, CLASS_ANNO_LEVEL : Annotation> protected constructor(
  private val classLevelPropertiesKey: TypedStoreKey<List<DATA_HOLDER>>,
  private val annotation: Class<ANNO>,
  private val annotationForClassLevel: Class<CLASS_ANNO_LEVEL>,

  ) : InvocationInterceptor, BeforeAllCallback, AfterAllCallback {


  override fun beforeAll(context: ExtensionContext) {
    val annotations = AnnotationSupport.findRepeatableAnnotations(context.testClass, annotationForClassLevel)
    if (annotations.isNotEmpty()) {
      context[classLevelPropertiesKey] = annotations.map { annotation ->
        setClassPropertyValue(annotation)
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
    val annotations = AnnotationSupport.findRepeatableAnnotations(invocationContext.targetClass, annotation) +
                      AnnotationSupport.findRepeatableAnnotations(invocationContext.executable, annotation)
    if (annotations.isEmpty()) {
      return invocation.proceed()
    }
    val valuesBefore = annotations.map { annotation ->
      setPropertyValue(annotation)
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

  protected abstract fun setPropertyValue(annotation: ANNO): DATA_HOLDER
  protected abstract fun setClassPropertyValue(annotation: CLASS_ANNO_LEVEL): DATA_HOLDER

  protected abstract fun resetPropertyValue(oldValue: DATA_HOLDER)
}
