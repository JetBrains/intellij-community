// COMPILER_ARGUMENTS: -Xcompanion-blocks
// PROBLEM: none
// K2_ERROR: UNSUPPORTED_FEATURE
// K2_ERROR: WRONG_MODIFIER_CONTAINING_DECLARATION
class Column

<caret>public companion inline fun Column.bar(): Int = 5
