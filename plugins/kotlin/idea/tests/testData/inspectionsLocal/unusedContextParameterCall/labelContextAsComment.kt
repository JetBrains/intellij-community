// COMPILER_ARGUMENTS: -Xcontext-parameters
fun foo() {}

fun test() {
    <caret>context("") {
        val s = "see return@context docs"
        // return@context in a comment
        foo()
        return@context
    }
}