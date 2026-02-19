// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KMutableProperty

class C(var foo: String)

fun bar(f: () -> KMutableProperty<*>) {}

fun test(c: C) {
    bar { c::foo }<caret>
}