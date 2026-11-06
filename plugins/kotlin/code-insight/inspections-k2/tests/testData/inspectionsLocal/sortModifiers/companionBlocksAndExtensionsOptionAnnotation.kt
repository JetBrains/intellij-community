// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: Modifiers should be placed directly before the relevant element
class Column

<caret>public @Suppress("unused") inline companion fun Column.bar(): Int = 5