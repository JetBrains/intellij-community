// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions -XXLanguage:+ContextParameters
// PROBLEM: Context parameters should follow annotations
class Column

context(value: Int)
<caret>@Suppress("unused")
public inline companion fun Column.bar(): Int = value
