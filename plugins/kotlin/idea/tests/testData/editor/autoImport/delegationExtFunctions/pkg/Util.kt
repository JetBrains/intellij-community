package pkg

import kotlin.reflect.KProperty

class A(var value: String) {
}
operator fun <V> A.getValue(thisRef: V, property: KProperty<*>): String = value
operator fun A.setValue(thisObj: Any?, property: KProperty<*>, value: String) { this.value = value }