// IS_APPLICABLE: false
// PROBLEM: none

import kotlin.reflect.KProperty

fun <P : Any> p(p: KProperty<P>) {}

class B {
    val s: String = ""
    init {
        p(<caret>this::s)
    }
}