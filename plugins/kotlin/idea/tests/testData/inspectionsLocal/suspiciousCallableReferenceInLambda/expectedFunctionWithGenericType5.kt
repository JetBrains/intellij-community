// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KMutableProperty

class C(var foo: String)

fun <T : KMutableProperty<*>> bar(f: () -> T) {}

fun test(c: C) {
    bar { c::foo }<caret>
}