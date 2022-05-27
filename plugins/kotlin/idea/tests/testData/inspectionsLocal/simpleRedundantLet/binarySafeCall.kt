// PROBLEM: none
// WITH_STDLIB
// ERROR: Type mismatch: inferred type is Int? but Int was expected

val x = 1
val y = x.<caret>let { it + it?.hashCode() }