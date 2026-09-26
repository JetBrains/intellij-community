// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5
fun unusedString() {}

context(<caret>s: String)
fun test() {
    print(::unusedString)
}