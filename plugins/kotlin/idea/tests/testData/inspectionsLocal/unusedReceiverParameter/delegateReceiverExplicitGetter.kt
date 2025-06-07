// PROBLEM: none
// IGNORE_K1

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

val Int<caret>.x by GetReceiverValue()
    get

class GetReceiverValue<R>() : ReadOnlyProperty<R, R> {
    override fun getValue(thisRef: R, property: KProperty<*>): R = thisRef
}