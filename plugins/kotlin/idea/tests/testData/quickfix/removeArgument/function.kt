// "Remove argument" "true"
// K2_ERROR: TOO_MANY_ARGUMENTS
class Bar(s: String, i: Int) {
    fun foo(s: String) {
    }
}

fun main() {
    val b = Bar("2", 1)
    b.foo("a", 1<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveArgumentFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveArgumentFix