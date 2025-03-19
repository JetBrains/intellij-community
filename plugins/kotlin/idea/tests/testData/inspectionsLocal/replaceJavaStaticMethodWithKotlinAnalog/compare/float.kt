// WITH_STDLIB
// ERROR: The floating-point literal does not conform to the expected type Float
// ERROR: The floating-point literal does not conform to the expected type Float
// K2_ERROR: Argument type mismatch: actual type is 'Double', but 'Float' was expected.
// K2_ERROR: Argument type mismatch: actual type is 'Double', but 'Float' was expected.

fun foo() {
    val t = java.lang.Float.<caret>compare(5.0, 6.0)
}
