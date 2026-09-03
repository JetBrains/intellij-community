// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

val state = MutableStateFlow(<caret>listOf(1, 2))