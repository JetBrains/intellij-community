// COMPILER_ARGUMENTS: -Xcontext-parameters
fun produce(): Int = 1

fun test() {
    val r = <caret>context("") {
        loop@ for (i in 0..10) {
            if (i == 5) break@loop
        }
        produce()
    }
}