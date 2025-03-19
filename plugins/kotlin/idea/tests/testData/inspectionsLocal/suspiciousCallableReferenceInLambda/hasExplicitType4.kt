// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KFunction0

fun String.test() {
    val x: () -> KFunction0<String> = { ::toString <caret>}
}
