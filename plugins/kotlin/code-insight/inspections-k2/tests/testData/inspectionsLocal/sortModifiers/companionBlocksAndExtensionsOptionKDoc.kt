// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions
// PROBLEM: KDoc should precede the modifiers
class Column

<caret>public inline companion /** Returns five. */ fun Column.bar(): Int = 5
