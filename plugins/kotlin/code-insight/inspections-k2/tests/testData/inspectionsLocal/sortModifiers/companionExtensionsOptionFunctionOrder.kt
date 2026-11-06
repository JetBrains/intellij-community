// COMPILER_ARGUMENTS: -XXLanguage:+CompanionExtensions
// PROBLEM: Non-canonical modifiers order
class Column

<caret>public companion inline fun Column.bar(): Int = 5
