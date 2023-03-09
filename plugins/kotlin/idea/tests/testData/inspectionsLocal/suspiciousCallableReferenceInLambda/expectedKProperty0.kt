// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KProperty0

class C(val foo: String)

fun bar(f: () -> KProperty0<*>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}