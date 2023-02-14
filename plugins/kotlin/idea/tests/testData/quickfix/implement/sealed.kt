// "Implement sealed class" "true"
// WITH_STDLIB
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

sealed class <caret>Base {
    abstract fun foo(): Int
}
