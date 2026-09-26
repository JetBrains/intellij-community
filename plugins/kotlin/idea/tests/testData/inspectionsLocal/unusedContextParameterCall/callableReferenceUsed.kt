// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5
context(s: String)
fun usesString() {}

fun test() {
    <caret>context("") {
        print(::usesString)
    }
}