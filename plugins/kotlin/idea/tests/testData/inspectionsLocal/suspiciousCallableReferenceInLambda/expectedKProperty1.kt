// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KProperty1

class C(val foo: String)

fun bar(f: () -> KProperty1<*, *>) {}

fun test() {
    bar { C::foo }<caret>
}