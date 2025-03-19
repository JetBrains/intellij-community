// FIR_IDENTICAL
// CHECK_SYMBOL_NAMES
// HIGHLIGHTER_ATTRIBUTES_KEY
class SimpleClassInFunction {
    <caret>
    fun foo() {

    }

    fun bar() {
    }
}

fun foo(): String {
    val x = 1
    return "$x"
}