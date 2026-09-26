// PROBLEM: none
// WITH_STDLIB
import kotlin.reflect.KProperty

class A

object B {
    operator fun A.provideDelegate(nothing: Nothing?, property: KProperty<*>) = Any()
}

private operator fun Any.getValue(nothing: Nothing?, property: KProperty<*>): Any {
    TODO("Not yet implemented")
}

fun main() {
    <caret>with(B) {
        val x by A()
    }
}