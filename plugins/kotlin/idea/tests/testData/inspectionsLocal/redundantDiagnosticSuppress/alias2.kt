// PROBLEM: none

import kotlin.Suppress as MySuppress

@MySuppress("<caret>UNUSED_EXPRESSION")
class A {
    fun check() {
        4
    }
}
