// PROBLEM: none
// WITH_STDLIB
// COMPILER_ARGUMENTS: -Xcontext-parameters

fun foo(x: context(String) () -> Unit) {
    wi<caret>th("") {
        x()
    }
}
