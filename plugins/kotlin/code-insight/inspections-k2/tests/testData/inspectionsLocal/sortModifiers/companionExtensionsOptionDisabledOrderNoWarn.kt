// COMPILER_ARGUMENTS: -XXLanguage:-CompanionExtensions
// PROBLEM: none
// K2_ERROR: UNSUPPORTED_FEATURE
// K2_ERROR: WRONG_MODIFIER_CONTAINING_DECLARATION
class Column

<caret>inline public companion fun Column.bar(): Int = 5
