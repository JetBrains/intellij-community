// "Reorder parameters" "true"
fun foo(
    x: String = y<caret>,
    y: String = "OK"
) = Unit

fun main() {
    foo("x", "y")
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix