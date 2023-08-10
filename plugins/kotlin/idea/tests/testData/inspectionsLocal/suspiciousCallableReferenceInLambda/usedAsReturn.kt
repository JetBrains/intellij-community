// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KFunction0

fun foo(): List<KFunction0<String>> {
    return listOf(1, 2, 3).map { it::toString <caret>}
}