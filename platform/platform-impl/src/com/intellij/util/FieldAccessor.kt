// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Ref
import org.jetbrains.annotations.NonNls
import java.lang.reflect.Field

/**
 * Thread unsafe field accessor.
 *
 * @param <E> the type of the field's class
 * @param <T> the type of the field
</T></E> */
class FieldAccessor<E, T> @JvmOverloads constructor(private val aClass: Class<E>,
                                                    private val name: @NonNls String,
                                                    private val type: Class<T>? = null) {
  private var fieldRef: Ref<Field?>? = null
  val isAvailable: Boolean
    get() {
      if (fieldRef == null) {
        fieldRef = Ref()
        var result: Field? = null
        run {
          var aClass: Class<*>? = this.aClass
          while (aClass != null) {
            for (candidate in aClass.declaredFields) {
              if (name == candidate.name && (type == null || type.isAssignableFrom(candidate.type))) {
                candidate.isAccessible = true
                result = candidate
                break
              }
            }
            aClass = aClass.superclass
          }
        }
        if (result == null) {
          LOG.warn("Field not found: " + aClass.name + "." + name)
          return false
        }
        fieldRef!!.set(result)
        fieldRef!!.get()!!.isAccessible = true
      }
      return fieldRef!!.get() != null
    }

  operator fun get(`object`: E?): T? {
    if (!isAvailable) {
      return null
    }
    try {
      return fieldRef!!.get()!!.get(`object`) as T
    }
    catch (e: IllegalAccessException) {
      LOG.warn("Field not accessible: " + aClass.name + "." + name)
    }
    return null
  }

  operator fun set(`object`: E?, value: T?) {
    if (!isAvailable) {
      return
    }
    try {
      fieldRef!!.get()!![`object`] = value
    }
    catch (e: IllegalAccessException) {
      LOG.warn("Field not accessible: " + aClass.name + "." + name)
    }
  }

  companion object {
    private val LOG = Logger.getInstance(FieldAccessor::class.java)
  }
}