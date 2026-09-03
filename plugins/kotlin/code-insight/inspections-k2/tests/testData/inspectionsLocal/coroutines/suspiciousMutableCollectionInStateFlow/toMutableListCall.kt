// WITH_COROUTINES
// PROBLEM: 'MutableStateFlow' emits no new value after a mutation of this collection
// FIX: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

fun test(source: List<Int>) {
    val state = MutableStateFlow(<caret>source.toMutableList())
    println(state)
}