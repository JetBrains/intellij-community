// WITH_COROUTINES
// PROBLEM: 'MutableStateFlow' emits no new value after a mutation of this collection
// FIX: Use a read-only collection type
package test

import kotlinx.coroutines.flow.MutableStateFlow

// This declaration shadows 'kotlin.collections.listOf'.
fun listOf(value: Int): String = value.toString()

val state = MutableStateFlow(<caret>mutableListOf(1))