// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("RegistryUtils")

package org.jetbrains.kotlin.idea.base.util

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.NonNls
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun registryFlag(@NonNls key: String): ReadWriteProperty<Any?, Boolean> {
    return object : ReadWriteProperty<Any?, Boolean> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = Registry.`is`(key)
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) = Registry.get(key).setValue(value)
    }
}