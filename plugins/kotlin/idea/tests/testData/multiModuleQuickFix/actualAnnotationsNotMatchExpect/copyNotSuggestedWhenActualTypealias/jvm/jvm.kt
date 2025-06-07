// "Copy mismatched annotation 'Ann' from 'expect' to 'actual' declaration (may change semantics)" "false"
// IGNORE_IRRELEVANT_ACTIONS
// DISABLE_ERRORS
// FIR_COMPARISON

class FooImpl

actual typealias Foo<caret> = FooImpl
