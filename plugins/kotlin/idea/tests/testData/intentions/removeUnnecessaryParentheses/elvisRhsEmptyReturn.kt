// IS_APPLICABLE: false
// WITH_RUNTIME
// AFTER-WARNING: Elvis operator (?:) always returns the left operand of non-nullable type String
fun foo() {
    bar(<caret>("" ?: return) in listOf(""))
}

fun bar(arg: Boolean) {}