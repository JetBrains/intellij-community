// COMPILER_ARGUMENTS: -Xcollection-literals
// PROBLEM: none
class Result<T> {
    companion object { operator fun <T> of(vararg elements: T): Result<T> = TODO() }
}

fun test() {
    val y = Resul<caret>t.of(1, 2, 3).toString()
}