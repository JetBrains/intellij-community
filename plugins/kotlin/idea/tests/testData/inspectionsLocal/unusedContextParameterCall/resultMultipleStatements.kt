// COMPILER_ARGUMENTS: -Xcontext-parameters
fun side() {}
fun produce(): Int = 1

fun test() {
    val result = <caret>context("") {
        side()
        produce()
    }
}