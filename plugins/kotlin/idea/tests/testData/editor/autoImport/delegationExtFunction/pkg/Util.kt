package pkg

import kotlin.reflect.KProperty

class A {
    fun getValue() {}
}
operator fun <V> A.getValue(thisRef: V, property: KProperty<*>): Any = TODO()
