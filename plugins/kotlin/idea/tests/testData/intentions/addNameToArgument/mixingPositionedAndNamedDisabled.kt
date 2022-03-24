// IS_APPLICABLE: false
// COMPILER_ARGUMENTS: -XXLanguage:-MixedNamedArgumentsInTheirOwnPosition

fun foo(s: String, b: Boolean){}

fun bar() {
    foo(<caret>"", true)
}