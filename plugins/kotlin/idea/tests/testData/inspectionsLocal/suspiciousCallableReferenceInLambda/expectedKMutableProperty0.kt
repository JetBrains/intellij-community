// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KMutableProperty0

class C(var foo: String)

fun bar(f: () -> KMutableProperty0<*>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}