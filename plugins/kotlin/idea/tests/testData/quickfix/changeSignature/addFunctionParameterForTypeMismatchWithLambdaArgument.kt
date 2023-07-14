// "Add 1st parameter to function 'foo'" "true"
fun foo(f: () -> Unit) {}

fun test() {
    foo(""<caret>) {}
}
// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix