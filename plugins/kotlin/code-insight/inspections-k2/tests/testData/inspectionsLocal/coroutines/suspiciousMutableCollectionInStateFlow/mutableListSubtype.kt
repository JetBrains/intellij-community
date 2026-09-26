// WITH_COROUTINES
// PROBLEM: 'MutableStateFlow' emits no new value after a mutation of this collection
// FIX: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

interface MyList : MutableList<Int>

fun test(list: MyList) {
    val state = MutableStateFlow(<caret>list)
    println(state)
}