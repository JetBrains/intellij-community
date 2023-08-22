// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KFunction

class C {
    fun foo() {}
}

fun <T : KFunction<*>> bar(f: () -> T) {}

fun test(c: C) {
    bar { c::foo }<caret>
}