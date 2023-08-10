// "Reorder parameters" "true"
fun Int.foo(
    x: Int = y<caret>,
    y: Int = this
) = Unit

fun main() {
    1.foo(2, 3)
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix