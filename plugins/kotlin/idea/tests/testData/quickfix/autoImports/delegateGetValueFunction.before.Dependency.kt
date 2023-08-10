package kotlinpackage.one

import kotlin.reflect.KProperty

class A {
    fun getValue() {}
}
operator fun <V> A.getValue(thisRef: V, property: KProperty<*>): Any = TODO()

class B

operator fun <V> B.getValue(thisRef: V, property: KProperty<*>): Any = TODO()
