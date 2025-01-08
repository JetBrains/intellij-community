// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.common.mock

import com.intellij.util.ReflectionUtil
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

/**
 * This function creates the Java proxy that throws [NotImplementedError] on any method invocation.
 * This util is useful in tests.
 * It allows creating a partially implemented mock object.
 */
inline fun <reified T> notImplemented(): T {
  return notImplemented(T::class.java)
}

fun <T> notImplemented(aClass: Class<T>): T {
  return ReflectionUtil.proxy(aClass, NotImplementedInvocationHandler())
}

private class NotImplementedInvocationHandler : InvocationHandler {
  override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
    val className = method.declaringClass.canonicalName
    val methodName = method.name
    val methodParameterTypes = method.parameterTypes.joinToString { it.simpleName }
    throw NotImplementedError("Method '$className#$methodName($methodParameterTypes)' isn't implemented")
  }
}