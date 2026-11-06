// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: Non-canonical modifiers order
// FIX: Sort modifiers
class Sample {
    companion {
        <caret>inline public fun bar(): Int = 5
    }
}
