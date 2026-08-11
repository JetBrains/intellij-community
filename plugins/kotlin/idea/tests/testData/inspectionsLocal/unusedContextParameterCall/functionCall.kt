// COMPILER_ARGUMENTS: -Xcontext-parameters
fun produce(): Int = 1
fun consume(x: Int) {}

fun test() {
    consume(<caret>context("") { produce() })
}