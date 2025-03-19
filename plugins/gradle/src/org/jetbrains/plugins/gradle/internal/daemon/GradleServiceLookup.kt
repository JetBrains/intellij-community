// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.internal.daemon

import org.gradle.util.GradleVersion
import java.lang.reflect.Type
import java.util.concurrent.ConcurrentHashMap

/**
 * This is an indirect implementation of org.gradle.internal.service.ServiceLookup.
 * @see GradleServiceLookupProxy
 */
class GradleServiceLookup {

  private val services: MutableMap<Type, Any> = ConcurrentHashMap()

  fun register(type: Type, instance: Any) {
    services[type] = instance
  }

  fun find(type: Type): Any? {
    return services[type]
  }

  fun get(type: Type): Any {
    return find(type)
           ?: throw IllegalArgumentException("The type $type is not registered. Gradle version: ${GradleVersion.current()}")
  }

  fun get(type: Type, clazz: Class<out Annotation>): Any {
    val result = get(type)
    if (clazz.isInstance(result)) {
      return result
    }
    throw IllegalStateException("The $type instance expected to be ${clazz}, but was ${result.javaClass}. " +
                                "Gradle version: ${GradleVersion.current()}")
  }
}