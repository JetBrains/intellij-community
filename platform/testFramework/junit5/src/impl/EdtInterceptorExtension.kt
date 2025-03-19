// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import com.intellij.testFramework.junit5.RunInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt
import com.intellij.testFramework.junit5.RunMethodInEdt.WriteIntentMode
import com.intellij.testFramework.runInEdtAndGet
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.extension.DynamicTestInvocationContext
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.InvocationInterceptor
import org.junit.jupiter.api.extension.InvocationInterceptor.Invocation
import org.junit.jupiter.api.extension.ReflectiveInvocationContext
import org.junit.platform.commons.support.AnnotationSupport
import java.lang.reflect.Constructor
import java.lang.reflect.Method

@TestOnly
internal class EdtInterceptorExtension : InvocationInterceptor {

  override fun interceptBeforeAllMethod(
    invocation: Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun <T> interceptTestClassConstructor(
    invocation: Invocation<T>,
    invocationContext: ReflectiveInvocationContext<Constructor<T>>,
    extensionContext: ExtensionContext,
  ): T {
    return intercept(invocation, invocationContext)
  }

  override fun interceptBeforeEachMethod(
    invocation: Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptTestMethod(
    invocation: Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptTestTemplateMethod(
    invocation: Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun <T> interceptTestFactoryMethod(
    invocation: Invocation<T>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ): T {
    if (shouldIntercept(invocationContext)) {
      extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).put(testFactoryWasInterceptedKey, true)
      val writeIntent = getWriteIntent(invocationContext)
      extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).put(testFactoryNeedsWriteIntentKey, writeIntent)
      return intercept(invocation, writeIntent)
    }
    else {
      return invocation.proceed()
    }
  }

  override fun interceptDynamicTest(
    invocation: Invocation<Void>,
    invocationContext: DynamicTestInvocationContext,
    extensionContext: ExtensionContext,
  ) {
    if (extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(testFactoryWasInterceptedKey) == true) {
      val writeIntent = extensionContext.getStore(ExtensionContext.Namespace.GLOBAL).get(testFactoryNeedsWriteIntentKey) as Boolean
      intercept(invocation, writeIntent)
    }
    else {
      invocation.proceed()
    }
  }

  override fun interceptAfterEachMethod(
    invocation: Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  override fun interceptAfterAllMethod(
    invocation: Invocation<Void>,
    invocationContext: ReflectiveInvocationContext<Method>,
    extensionContext: ExtensionContext,
  ) {
    intercept(invocation, invocationContext)
  }

  @TestOnly
  private companion object {

    const val testFactoryWasInterceptedKey = "test factory was intercepted"
    const val testFactoryNeedsWriteIntentKey = "test factory need write intent"

    fun <T> intercept(invocation: Invocation<T>, invocationContext: ReflectiveInvocationContext<*>): T {
      if (shouldIntercept(invocationContext)) {
        return intercept(invocation, getWriteIntent(invocationContext))
      }
      else {
        return invocation.proceed()
      }
    }

    fun shouldIntercept(invocationContext: ReflectiveInvocationContext<*>): Boolean {
      val runInEdt = AnnotationSupport.findAnnotation(invocationContext.targetClass, RunInEdt::class.java).get()
      return runInEdt.allMethods || AnnotationSupport.findAnnotation(invocationContext.executable, RunMethodInEdt::class.java).isPresent
    }

    fun getWriteIntent(invocationContext: ReflectiveInvocationContext<*>): Boolean {
      val runInEdt = AnnotationSupport.findAnnotation(invocationContext.targetClass, RunInEdt::class.java).get()
      val runMethodInEdtOpt = AnnotationSupport.findAnnotation(invocationContext.executable, RunMethodInEdt::class.java)
      if (runMethodInEdtOpt.isEmpty) {
        return runInEdt.writeIntent
      }
      else {
        when (runMethodInEdtOpt.get().writeIntent) {
          WriteIntentMode.True -> return true
          WriteIntentMode.False -> return false
          WriteIntentMode.Default -> return runInEdt.writeIntent
        }
      }
    }

    fun <T> intercept(invocation: Invocation<T>, writeIntent: Boolean): T {
      return runInEdtAndGet(writeIntent) {
        invocation.proceed()
      }
    }
  }
}
