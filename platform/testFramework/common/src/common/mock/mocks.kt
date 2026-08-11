// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("Mocks")

package com.intellij.testFramework.common.mock

import com.intellij.util.ReflectionUtil
import kotlin.reflect.KProperty1

/**
 * This function creates the Java proxy that throws [NotImplementedError] on any method invocation.
 * This util is useful in tests.
 * It allows creating a partially implemented mock object.
 */
inline fun <reified T> notImplemented(): T {
  return notImplemented(T::class.java)
}

fun <T> notImplemented(aClass: Class<T>): T = ReflectionUtil.proxy(aClass) { _, method, _ ->
  val className = method.declaringClass.canonicalName
  val methodName = method.name
  val methodParameterTypes = method.parameterTypes.joinToString { it.simpleName }
  throw NotImplementedError("Method '$className#$methodName($methodParameterTypes)' isn't implemented")
}

fun <Receiver : Any, Result : Any> Receiver.requireImplemented(property: KProperty1<Receiver, Result?>): Result =
  property.get(this) ?: throw NotImplementedError("${this.javaClass.simpleName}.${property.name} is not defined")
