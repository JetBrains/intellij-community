// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.util.GradleVersion
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.lang.reflect.Type

object GradleServiceLookupProxy {

  private class LookupInvocationHandler(private val instance: GradleServiceLookup) : InvocationHandler {

    override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any? {
      val methodName = method.name
      val argumentCount = args.size
      return when {
        "find" == methodName && argumentCount == 1 && args[0] is Type -> instance.find(args[0] as Type)
        "get" == methodName && argumentCount == 1 && args[0] is Type -> instance.get(args[0] as Type)
        "get" == methodName && argumentCount == 2 && args[0] is Type && args[1] is Class<*>
          -> instance.get(args[0] as Type, args[1] as Class<out Annotation?>)
        else -> throw IllegalArgumentException("Unable to find method $methodName with $argumentCount arguments")
      }
    }
  }

  fun <T> newProxyInstance(lookup: GradleServiceLookup): T {
    val clazz: Class<*>
    try {
      clazz = Class.forName("org.gradle.internal.service.ServiceLookup")
    }
    catch (e: ClassNotFoundException) {
      throw IllegalStateException(
        "Unable to load ServiceLookup class for " + GradleVersion.current() + ". This class should be available from Gradle 8.8"
      )
    }
    val proxy = Proxy.newProxyInstance(
      clazz.classLoader,
      arrayOf(clazz),
      LookupInvocationHandler(lookup)
    ) as T
    return proxy
  }
}