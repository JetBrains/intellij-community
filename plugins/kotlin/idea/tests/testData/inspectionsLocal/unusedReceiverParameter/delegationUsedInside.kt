// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KProperty

class A

object B {
    operator fun A.getValue(nothing: Nothing?, property: KProperty<*>): Any {
        TODO()
    }
}

fun <caret>B.main() {
    val x by A()
}