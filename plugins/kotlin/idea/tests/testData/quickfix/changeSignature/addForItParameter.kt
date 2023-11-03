// "Add parameter to constructor 'Foo'" "true"
// WITH_STDLIB

class Foo

fun test(name: String) {
    name.also { Foo(it<caret>) }
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix
// IGNORE_K2