// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: Non-canonical modifiers order
class Column

<caret>public companion // Keep this comment.
inline fun Column.bar(): Int = 5
