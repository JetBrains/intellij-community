// "Add parameter to constructor 'Foo'" "true"
// DISABLE-ERRORS
enum class Foo {
    A("A"<caret>),
    B("B")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// IGNORE_K2