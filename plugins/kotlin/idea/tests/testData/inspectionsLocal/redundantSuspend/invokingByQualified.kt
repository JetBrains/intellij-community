// PROBLEM: none
// WITH_STDLIB
<caret>suspend fun List<suspend () -> Unit>.invokeFirst() = this.first()()