package base

import kotlin.reflect.KProperty

class State(val value: String)

operator fun State.getValue(thisObj: Any?, property: KProperty<*>): String = value