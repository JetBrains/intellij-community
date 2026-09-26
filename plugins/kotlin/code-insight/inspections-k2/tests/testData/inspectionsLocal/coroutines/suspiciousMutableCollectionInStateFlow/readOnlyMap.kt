// WITH_COROUTINES
// PROBLEM: none
package test

import kotlinx.coroutines.flow.MutableStateFlow

val state = MutableStateFlow(<caret>mapOf("a" to 1))