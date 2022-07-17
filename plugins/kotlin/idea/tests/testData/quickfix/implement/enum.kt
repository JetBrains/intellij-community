// "Create subclass" "false"
// ACTION: Convert to sealed class
// ACTION: Create test
// ACTION: Rename file to My.kt

enum class <caret>My {
    SINGLE {
        override fun foo(): Int = 0
    };

    abstract fun foo(): Int
}
