// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: Non-canonical modifiers order
// FIX: Sort modifiers
class Column

<caret>inline public companion fun Column.bar(): Int = 5
