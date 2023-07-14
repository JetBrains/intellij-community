// "Add parameter to function 'foo'" "true"
// DISABLE-ERRORS
fun foo() {}

fun bar(f: (String) -> Unit) {}

fun test() {
    bar {
        foo(it<caret>)
    }
}


// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddFunctionParametersFix