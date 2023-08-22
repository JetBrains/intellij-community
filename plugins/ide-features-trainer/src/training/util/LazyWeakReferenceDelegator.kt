// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package training.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class LazyWeakReferenceDelegator<T>(private val objGetter: () -> T?) : ReadOnlyProperty<Any?, T?> {
  private var reference: WeakReference<T>? = null

  override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
    if (reference == null) {
      reference = WeakReference(objGetter())
    }
    return reference?.get()
  }

  fun reset() {
    reference = null
  }
}