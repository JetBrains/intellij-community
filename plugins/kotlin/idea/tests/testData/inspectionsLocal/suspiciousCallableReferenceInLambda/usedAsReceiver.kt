// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KFunction0

fun test() {
    listOf(1, 2, 3).map { it::toString <caret>}.foo()
}

fun List<KFunction0<String>>.foo() {
    forEach { it.invoke() }
}

