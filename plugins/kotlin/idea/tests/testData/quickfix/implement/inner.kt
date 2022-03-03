// "Implement abstract class" "true"
// WITH_STDLIB
// SHOULD_BE_AVAILABLE_AFTER_EXECUTION

class Container {
    inner abstract class <caret>Base {
        abstract fun foo(): String
    }
}
