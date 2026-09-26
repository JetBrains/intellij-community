// PROBLEM: none
// WITH_STDLIB

import kotlin.reflect.KFunction0

val foo: List<KFunction0<String>> = listOf(1, 2, 3).map { it::toString <caret>}