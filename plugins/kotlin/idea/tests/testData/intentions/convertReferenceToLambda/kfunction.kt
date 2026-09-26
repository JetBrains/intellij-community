// IS_APPLICABLE: false
// PROBLEM: none

import kotlin.reflect.KFunction

fun <P : Any> p(p: KFunction<P>) {}

class B {
    fun getS(): String = ""
    init {
        p(<caret>this::getS)
    }
}