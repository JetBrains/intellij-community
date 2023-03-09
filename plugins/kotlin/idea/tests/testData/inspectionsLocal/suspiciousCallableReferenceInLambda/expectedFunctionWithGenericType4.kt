// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KProperty

class C(val foo: String)

fun <T : KProperty<*>> bar(f: () -> T) {}

fun test(c: C) {
    bar { c::foo }<caret>
}