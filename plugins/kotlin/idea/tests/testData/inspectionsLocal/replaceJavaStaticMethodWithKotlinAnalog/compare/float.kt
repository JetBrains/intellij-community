// WITH_STDLIB
// K2-ERROR: Argument type mismatch: actual type is 'Double', but 'Float' was expected.
// K2-ERROR: Argument type mismatch: actual type is 'Double', but 'Float' was expected.

fun foo() {
    val t = java.lang.Float.<caret>compare(5.0, 6.0)
}
