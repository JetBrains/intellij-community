// WITH_STDLIB
// K2_ERROR: Argument type mismatch: actual type is 'Double', but 'Float' was expected.
// ERROR: The floating-point literal does not conform to the expected type Float

fun foo() {
    val t = java.lang.Float.<caret>toString(5.0)
}
