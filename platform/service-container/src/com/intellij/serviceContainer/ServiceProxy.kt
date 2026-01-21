// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

internal interface ServiceProxyInstrumentation {
  fun setForwarding(forwardTo: Lazy<Any>?)
}

internal class ServiceProxy(private val originalDelegate: Any) : InvocationHandler, ServiceProxyInstrumentation {

  @Volatile
  private var forwardTo: Lazy<Any>? = null

  companion object {
    fun <T> createInstance(
      superClass: Class<T>,
      delegate: Any,
    ): T {
      return Proxy.newProxyInstance(
        superClass.classLoader,
        arrayOf(superClass, ServiceProxyInstrumentation::class.java),
        ServiceProxy(delegate)) as T
    }
  }

  override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
    if (method.declaringClass == ServiceProxyInstrumentation::class.java) {
      return method.invoke(this, *(args ?: emptyArray()))
    }

    val delegate = getDelegate()
    return method.invoke(delegate, *(args ?: emptyArray()))
  }

  private fun getDelegate(): Any {
    return forwardTo?.value ?: originalDelegate
  }

  override fun setForwarding(forwardTo: Lazy<Any>?) {
    this.forwardTo = forwardTo
  }
}