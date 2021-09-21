package org.jetbrains.plugins.feature.suggester.util

import java.lang.ref.WeakReference
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class WeakReferenceDelegator<T>(obj: T? = null) : ReadWriteProperty<Any?, T?> {
    private var reference: WeakReference<T>?

    init {
        this.reference = obj?.let { WeakReference(it) }
    }

    override fun getValue(thisRef: Any?, property: KProperty<*>): T? {
        return reference?.get()
    }

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T?) {
        reference = value?.let { WeakReference(it) }
    }
}
