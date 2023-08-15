// "Remove mismatched annotation 'Ann' from expect declaration (may change semantics)" "true"
// DISABLE-ERRORS
// FIR_COMPARISON

class FooImpl

actual typealias Foo<caret> = FooImpl
