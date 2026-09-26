// COMPILER_ARGUMENTS: -Xcontext-parameters
fun produce(): Int = 1

fun test() {
    val r = <caret>context("") {
        val inner = context("x") {
            return@context produce()
        }
        inner
    }
}