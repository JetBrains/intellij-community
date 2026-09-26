// WITH_COROUTINES
// PROBLEM: 'MutableStateFlow' emits no new value after a mutation of this collection
// FIX: Use a read-only collection type
package test

import kotlinx.coroutines.flow.MutableStateFlow

val state = MutableStateFlow(value = <caret>mutableListOf<Int>())