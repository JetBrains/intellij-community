// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.util.Key
import kotlin.reflect.KProperty

class DataNodeUserDataProperty<in R : DataNode<*>, T : Any>(val key: Key<T>) {
    operator fun getValue(thisRef: R, property: KProperty<*>) = thisRef.getUserData(key)

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T?) = thisRef.putUserData(key, value)
}

class CopyableDataNodeUserDataProperty<in R : DataNode<*>, T : Any>(val key: Key<T>) {
    operator fun getValue(thisRef: R, property: KProperty<*>) = thisRef.getCopyableUserData(key)

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T?) = thisRef.putCopyableUserData(key, value)
}

class NotNullableCopyableDataNodeUserDataProperty<in R : DataNode<*>, T : Any>(val key: Key<T>, val defaultValue: T) {
    operator fun getValue(thisRef: R, property: KProperty<*>) = thisRef.getCopyableUserData(key) ?: defaultValue

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        thisRef.putCopyableUserData(key, if (value != defaultValue) value else null)
    }
}

class FactoryCopyableDataNodeUserDataProperty<in R : DataNode<*>, T : Any>(val key: Key<T>, private val factory: () -> T) {
    operator fun getValue(thisRef: R, property: KProperty<*>): T =
        thisRef.getCopyableUserData(key) ?: factory().also { value -> thisRef.putCopyableUserData(key, value) }

    operator fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        thisRef.putCopyableUserData(key, value)
    }
}
