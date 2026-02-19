// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KFunction0

fun test() {
    foo(listOf(1, 2, 3).map { it::toString <caret>})
}

fun foo(list: List<KFunction0<String>>) {
    list.forEach { it.invoke() }
}

