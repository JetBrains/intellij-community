// IGNORE_FE10_BINDING_BY_FIR
// PROBLEM: none
// WITH_STDLIB
<caret>suspend fun List<suspend () -> Unit>.invokeFirst() = this.first()()