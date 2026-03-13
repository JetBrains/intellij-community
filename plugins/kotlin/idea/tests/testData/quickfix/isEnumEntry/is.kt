// "Replace with '=='" "true"
// K2_ERROR: 'is' over enum entry is prohibited. Use comparison instead.
enum class Foo { A }

fun test(foo: Foo): Boolean = foo is <caret>Foo.A

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceIsEnumEntryWithComparisonFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ReplaceIsEnumEntryWithComparisonFix