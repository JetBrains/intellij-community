// PROBLEM: none
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is Int? but Int was expected
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

val x = 1
val y = x.<caret>let { it + it?.hashCode() }