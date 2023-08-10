// WITH_STDLIB
// PROBLEM: none

import kotlin.reflect.KMutableProperty1

class C(var foo: String)

fun bar(f: () -> KMutableProperty1<*, *>) {}

fun test() {
    bar { C::foo }<caret>
}