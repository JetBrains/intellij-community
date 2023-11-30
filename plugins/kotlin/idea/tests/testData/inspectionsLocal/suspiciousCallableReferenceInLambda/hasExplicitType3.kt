// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KFunction0

fun List<Int>.test() {
    val foo: List<KFunction0<String>> = map { it::toString <caret>}
}
