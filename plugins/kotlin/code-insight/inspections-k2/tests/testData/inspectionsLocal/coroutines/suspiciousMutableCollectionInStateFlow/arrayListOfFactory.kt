// WITH_COROUTINES
// PROBLEM: 'MutableStateFlow' emits no new value after a mutation of this collection
// FIX: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

val state = MutableStateFlow(<caret>arrayListOf(1, 2))