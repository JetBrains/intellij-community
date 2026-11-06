// COMPILER_ARGUMENTS: -Xcompanion-blocks-and-extensions -XXLanguage:+ContextParameters
// PROBLEM: KDoc should precede the modifiers
class Column

<caret>inline context(value: Int)
@Suppress("unused")
/** Returns the context value. */
public companion fun Column.bar(): Int = value
