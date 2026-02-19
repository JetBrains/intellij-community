// PROBLEM: none
// WITH_STDLIB
import kotlin.reflect.KProperty

class A

object B {
    operator fun A.getValue(nothing: Nothing?, property: KProperty<*>): Any {
        TODO()
    }
}

fun main() {
    <caret>with(B) {
        val x by A()
    }
}