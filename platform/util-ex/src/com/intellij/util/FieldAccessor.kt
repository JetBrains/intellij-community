// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field

/**
 * Thread unsafe field accessor.
 *
 * @param <E> the type of the field's class
 * @param <T> the type of the field
</T></E> */
class FieldAccessor<E, T>(private val aClass: Class<E>,
                          private val name: @NonNls String,
                          private val type: Class<T>? = null) {
  @ApiStatus.ScheduledForRemoval
  @Deprecated("Please pass type")
  constructor(aClass: Class<E>, name: @NonNls String) : this(aClass = aClass, name = name, type = null)

  private val lookup = MethodHandles.privateLookupIn(aClass, MethodHandles.lookup())

  private val getter: MethodHandle? by lazy {
    if (type == null) {
      field?.let {
        lookup.unreflectGetter(field)
      }
    }
    else {
      try {
        lookup.findGetter(aClass, name, type)
      }
      catch (e: NoSuchFieldException) {
        warnFieldNotFound()
        null
      }
    }
  }

  private val setter: MethodHandle? by lazy {
    if (type == null) {
      field?.let {
        lookup.unreflectSetter(field)
      }
    }
    else {
      try {
        lookup.findSetter(aClass, name, type)
      }
      catch (e: NoSuchFieldException) {
        warnFieldNotFound()
        null
      }
    }
  }

  private val field: Field? by lazy {
    var aClass: Class<*>? = this.aClass
    while (aClass != null) {
      for (candidate in aClass.declaredFields) {
        if (name == candidate.name && (type == null || type.isAssignableFrom(candidate.type))) {
          candidate.isAccessible = true
          return@lazy candidate
        }
      }
      aClass = aClass.superclass
    }
    warnFieldNotFound()
    null
  }

  val isAvailable: Boolean
    get() = this.field != null

  operator fun get(`object`: E?): T? {
    @Suppress("UNCHECKED_CAST")
    return (getter ?: return null).invoke(`object`) as T
  }

  operator fun set(`object`: E?, value: T?) {
    (setter ?: return).invoke(`object`, value)
  }

  private fun warnFieldNotFound() {
    logger<FieldAccessor<*, *>>().warn("Field not found: ${this.aClass.name}.$name")
  }
}