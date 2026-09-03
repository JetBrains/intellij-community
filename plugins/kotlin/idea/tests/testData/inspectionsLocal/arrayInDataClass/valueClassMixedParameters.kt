// WITH_STDLIB
// COMPILER_ARGUMENTS: -XXLanguage:+CustomEqualsInValueClasses
// DISABLE_K2_ERRORS

value class A(
    val a: <caret>IntArray,
    val b: Array<String>,
    val c: String,
    val d: Int,
)
