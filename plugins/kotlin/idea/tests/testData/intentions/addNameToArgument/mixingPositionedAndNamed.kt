// COMPILER_ARGUMENTS: -XXLanguage:+MixedNamedArgumentsInTheirOwnPosition
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 's' is never used

fun foo(s: String, b: Boolean){}

fun bar() {
    foo(<caret>"", true)
}
