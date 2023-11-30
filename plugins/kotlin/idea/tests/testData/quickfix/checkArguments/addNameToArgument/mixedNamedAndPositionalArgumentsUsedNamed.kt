// "Add name to argument: 'b = "FOO"'" "true"
// LANGUAGE_VERSION: 1.3

fun f(a: String, b: String) {}

fun g() {
    f(a = "BAR", <caret>"FOO")
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddNameToArgumentFix