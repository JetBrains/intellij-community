// COMPILER_ARGUMENTS: -Xcontext-parameters

fun foo(<caret>p: String) {
}

fun bar(a: Any) {
    foo(a.toString())
}
