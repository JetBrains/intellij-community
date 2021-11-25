// COMPILER_ARGUMENTS: -XXLanguage:+NewInference -XXLanguage:+MixedNamedArgumentsInTheirOwnPosition
// WITH_STDLIB
// AFTER-WARNING: Parameter 'b' is never used
// AFTER-WARNING: Parameter 's' is never used

fun foo(s: String, b: Boolean){}

fun bar() {
    foo(<caret>"", true)
}
