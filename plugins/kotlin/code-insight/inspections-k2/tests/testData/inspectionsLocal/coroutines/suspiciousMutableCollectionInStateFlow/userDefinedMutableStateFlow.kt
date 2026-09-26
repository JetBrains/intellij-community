// WITH_COROUTINES
// PROBLEM: none
package test

class MyFlow<T>(val value: T)

fun <T> MutableStateFlow(value: T): MyFlow<T> = MyFlow(value)

val state = MutableStateFlow(<caret>mutableListOf<Int>())