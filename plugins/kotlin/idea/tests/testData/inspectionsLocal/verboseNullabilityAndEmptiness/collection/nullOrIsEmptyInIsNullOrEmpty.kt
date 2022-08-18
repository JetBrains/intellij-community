// WITH_STDLIB
// PROBLEM: none
fun Collection<*>?.isNullOrEmpty(): Boolean = <caret>this == null || isEmpty()