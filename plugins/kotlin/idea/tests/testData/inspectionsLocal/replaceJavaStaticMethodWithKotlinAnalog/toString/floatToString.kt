// WITH_STDLIB
// K2-ERROR: Argument type mismatch: actual type is 'Double', but 'Float' was expected.

fun foo() {
    val t = java.lang.Float.<caret>toString(5.0)
}
