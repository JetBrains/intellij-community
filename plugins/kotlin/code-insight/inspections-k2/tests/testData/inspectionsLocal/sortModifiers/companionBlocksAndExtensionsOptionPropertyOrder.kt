// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: Non-canonical modifiers order
// FIX: Sort modifiers
class Column

<caret>public companion inline val Column.bar: Int
    get() = 5
