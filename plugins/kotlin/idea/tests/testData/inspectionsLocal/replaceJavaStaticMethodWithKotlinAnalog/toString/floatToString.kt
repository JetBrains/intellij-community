// WITH_STDLIB
// ERROR: The floating-point literal does not conform to the expected type Float
// K2_ERROR: ARGUMENT_TYPE_MISMATCH

fun foo() {
    val t = java.lang.Float.<caret>toString(5.0)
}
