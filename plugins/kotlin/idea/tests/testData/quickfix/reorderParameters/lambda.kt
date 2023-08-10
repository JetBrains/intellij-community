// "Reorder parameters" "true"
fun foo(
    x: () -> String = { y<caret> },
    y: String = "OK"
) = Unit

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReorderParametersFix