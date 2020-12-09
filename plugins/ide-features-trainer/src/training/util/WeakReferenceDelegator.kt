// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

// Maybe it can be moved to the platform
class WeakReferenceDelegator<T>(obj: T? = null): ReadWriteProperty<Any?, T?> {
  private var reference : WeakReference<T>?

  init {
    this.reference = obj?.let { WeakReference(it) }
  }

  override fun getValue(thisRef:Any? , property: KProperty<*>): T? {
    return reference?.get()
  }

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
    reference = value?.let { WeakReference(it) }
  }
}
