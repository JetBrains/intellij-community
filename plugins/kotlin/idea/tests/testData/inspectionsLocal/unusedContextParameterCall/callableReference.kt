// COMPILER_ARGUMENTS: -Xcontext-parameters -Xcallable-references-to-contextual
// LANGUAGE_VERSION: 2.5
fun unusedString() {}

fun test() {
    <caret>context("") {
        print(::unusedString)
    }
}