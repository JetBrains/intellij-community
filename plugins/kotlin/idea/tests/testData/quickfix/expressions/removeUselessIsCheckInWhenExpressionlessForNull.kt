// "Remove useless is check" "true"
fun foo() {
    when {
        <caret>null is Boolean -> {
        }
    }
}

// IGNORE_K2
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.RemoveUselessIsCheckFix