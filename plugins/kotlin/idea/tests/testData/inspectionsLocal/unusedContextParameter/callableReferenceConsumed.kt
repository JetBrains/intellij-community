// PROBLEM: none
// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5
context(s: String)
fun usesString() {}

context(<caret>s: String)
fun test() {
    print(::usesString)
}