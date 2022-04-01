// ERROR: Type mismatch: inferred type is Int? but Int was expected
// PROBLEM: none
// WITH_STDLIB

val x = 1
val y = x.<caret>let { it + it?.hashCode() }