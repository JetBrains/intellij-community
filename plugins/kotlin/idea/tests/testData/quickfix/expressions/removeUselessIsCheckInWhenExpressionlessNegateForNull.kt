// "Remove redundant 'is' check" "true"
fun foo() {
    when {
        <caret>null !is Boolean -> {
        }
    }
}

// IGNORE_K2
