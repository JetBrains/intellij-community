// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KFunction

class C {
    fun foo() {}
}

fun bar(f: () -> KFunction<Unit>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}