// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.dashboard

import java.util.concurrent.atomic.AtomicReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class AtomicObservableProperty<V>(initialValue: V,
                                  private val onChange: (oldValue: V, newValue: V) -> Unit) : ReadWriteProperty<Any?, V> {
  private val valueReference = AtomicReference(initialValue)

  override fun getValue(thisRef: Any?, property: KProperty<*>): V {
    return valueReference.get()
  }

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
    val oldValue = valueReference.getAndSet(value)
    onChange(oldValue, value)
  }
}