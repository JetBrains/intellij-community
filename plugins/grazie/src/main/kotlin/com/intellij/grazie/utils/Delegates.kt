package com.intellij.grazie.utils

import com.intellij.grazie.GrazieConfig
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

internal class LazyGrazieConfig<R, T>(val init: (GrazieConfig.State) -> Unit) : ReadWriteProperty<R, T> {
  private var value: T? = null

  override fun getValue(thisRef: R, property: KProperty<*>): T {
    if (value == null) init(GrazieConfig.get())
    return value!!
  }

  override fun setValue(thisRef: R, property: KProperty<*>, value: T) {
    this.value = value
  }
}

internal fun <R, T> lazyConfig(init: (GrazieConfig.State) -> Unit) = LazyGrazieConfig<R, T>(init)