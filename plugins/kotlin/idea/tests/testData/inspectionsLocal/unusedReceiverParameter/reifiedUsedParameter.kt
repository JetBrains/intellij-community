// PROBLEM: none
// WITH_STDLIB


import kotlin.reflect.typeOf

class Test<T>()

inline fun <reified T> <caret>Test<T>.typeOfElements() = typeOf<T>()