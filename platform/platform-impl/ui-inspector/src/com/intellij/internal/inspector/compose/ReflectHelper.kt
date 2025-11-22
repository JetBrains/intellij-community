// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.inspector.compose

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

internal object ReflectHelper {
    // Cache fields for performance in hot paths like recomposition tracking
    private val fieldCache = ConcurrentHashMap<String, Field>()

    fun getFieldValue(obj: Any, fieldName: String): Any? {
        val field = findField(obj, fieldName)
        return field?.get(obj)
    }

    fun setFieldValue(obj: Any, fieldName: String, value: Any?): Any? {
        val field = findField(obj, fieldName)
        return field?.set(obj, value)
    }

    private fun findField(obj: Any, fieldName: String): Field? {
        val key = "${obj.javaClass.name}.$fieldName"
        var field = fieldCache[key]
        if (field == null) {
            var cls = obj.javaClass
            while (cls != Object::class.java) {
                try {
                    field = cls.getDeclaredField(fieldName)
                    field!!.isAccessible = true
                    fieldCache[key] = field
                    break
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass ?: return null
                }
            }
        }
        return field
    }

    fun callMethod(obj: Any, methodName: String, vararg args: Any?): Any? {
        try {
            val method = obj.javaClass.getMethod(methodName)
            method.isAccessible = true
            return method.invoke(obj, *args)
        } catch (_: Exception) {
            return null
        }
    }
}
