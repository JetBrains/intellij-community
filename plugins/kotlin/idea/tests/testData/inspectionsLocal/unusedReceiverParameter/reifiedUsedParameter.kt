// PROBLEM: none
// WITH_STDLIB
// IGNORE_K1

import kotlin.reflect.typeOf

class Test<T>()

inline fun <reified T> <caret>Test<T>.typeOfElements() = typeOf<T>()