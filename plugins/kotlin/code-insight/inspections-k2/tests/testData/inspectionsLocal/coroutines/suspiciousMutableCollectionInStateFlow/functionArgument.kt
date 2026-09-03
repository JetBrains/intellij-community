// WITH_COROUTINES
// PROBLEM: 'MutableStateFlow' emits no new value after a mutation of this collection
// FIX: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

fun takeState(state: MutableStateFlow<MutableList<Int>>) {
    println(state)
}

fun test() {
    takeState(MutableStateFlow(<caret>mutableListOf()))
}