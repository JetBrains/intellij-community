// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import org.jetbrains.annotations.ApiStatus
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Deprecated("",
            replaceWith = ReplaceWith("instance.transform<S, T>(map, comap)",
                                      "com.intellij.openapi.observable.properties.transform"))
@ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
class PropertyView<R, S, T>(
  private val instance: ReadWriteProperty<R, S>,
  private val map: (S) -> T,
  private val comap: (T) -> S
) : ReadWriteProperty<R, T> {

  override fun getValue(thisRef: R, property: KProperty<*>): T {
    return map(instance.getValue(thisRef, property))
  }

  override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
    instance.setValue(thisRef, property, comap(value))
  }

  companion object {
    @Suppress("DEPRECATION")
    @Deprecated("", replaceWith = ReplaceWith("this.map<T>(transform)", "com.intellij.openapi.observable.properties.map"))
    fun <R, T> ReadWriteProperty<R, T>.map(transform: (T) -> T) = PropertyView(this, transform, { it })

    @Suppress("DEPRECATION")
    @Deprecated("", replaceWith = ReplaceWith("this.comap<T>(transform)", "com.intellij.openapi.observable.properties.comap"))
    fun <R, T> ReadWriteProperty<R, T>.comap(transform: (T) -> T) = PropertyView(this, { it }, transform)
  }
}