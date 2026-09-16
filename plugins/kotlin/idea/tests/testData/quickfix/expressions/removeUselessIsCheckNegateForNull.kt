// "Remove redundant 'is' check" "true"
fun foo() {
    if (<caret>null !is Boolean) {
    }
}

// IGNORE_K2
