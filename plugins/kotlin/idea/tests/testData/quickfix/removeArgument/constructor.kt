// "Remove argument" "true"
class Bar(s: String, i: Int) {
    fun foo(s: String) {

    }
}

fun main() {
    val b = Bar("2", 1, "2"<caret>)
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveArgumentFix