// IS_APPLICABLE: false
// WITH_STDLIB
fun getResult() = Pair(2, 2)

fun foo() {
    val (<caret>_, status) = getResult()
}