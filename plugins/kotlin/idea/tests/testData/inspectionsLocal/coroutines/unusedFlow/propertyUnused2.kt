// PROBLEM: Flow is constructed but not used
// FIX: none
// WITH_COROUTINES


import kotlinx.coroutines.flow.flowOf

suspend fun test() {
    val a = flowOf(1)
    a<caret>
}