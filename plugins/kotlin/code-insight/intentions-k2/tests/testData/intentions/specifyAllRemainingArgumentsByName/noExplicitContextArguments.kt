// IS_APPLICABLE: false
// SKIP_ERRORS_BEFORE
// SKIP_WARNINGS_AFTER
// SKIP_ERRORS_AFTER
// COMPILER_ARGUMENTS: -Xcontext-parameters -XXLanguage:-ExplicitContextArguments
// LANGUAGE_VERSION: 2.3
context(x: String)
fun foo(c: Int): String {
    return x + c
}

fun main() {
    f<caret>oo(0)
}