package a

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class C

interface MyProvider {
    operator fun C.provideDelegate(thisRef: Any, prop: KProperty<*>): ReadOnlyProperty<Any, C> = TODO()
}

object MyObject : MyProvider