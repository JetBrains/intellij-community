// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xopt-in=kotlin.RequiresOptIn
// PROBLEM: none

@OptIn(<caret>ExperimentalUnsignedTypes::class) fun foo(vararg values: ULong){}