// "Replace with '!='" "true"
enum class Foo { A }

fun test(foo: Foo): Boolean = foo !is <caret>Foo.A

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.IsEnumEntryFactory$ReplaceWithComparisonFix