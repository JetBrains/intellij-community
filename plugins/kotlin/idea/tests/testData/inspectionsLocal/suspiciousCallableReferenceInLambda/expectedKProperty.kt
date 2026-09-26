// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KProperty

class C(val foo: String)

fun bar(f: () -> KProperty<*>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}