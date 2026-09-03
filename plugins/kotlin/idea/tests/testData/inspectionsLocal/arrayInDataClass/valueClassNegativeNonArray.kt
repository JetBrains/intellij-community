// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses
// PROBLEM: none

@JvmInline
value class A(val <caret>a: String)
