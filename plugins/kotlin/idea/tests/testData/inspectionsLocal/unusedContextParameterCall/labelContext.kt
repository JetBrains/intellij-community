// COMPILER_ARGUMENTS: -Xcontext-parameters
fun side() {}
fun produce(): Int = 1

fun test() {
    val r = <caret>context("") {
        side()
        return@context produce()
    }
}