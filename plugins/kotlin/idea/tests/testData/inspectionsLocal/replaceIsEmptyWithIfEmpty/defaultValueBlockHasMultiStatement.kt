// WITH_STDLIB
fun foo() = listOf(1)

fun test(list: List<Int>): List<Int> {
    return <caret>if (list.isEmpty()) {
        println()
        foo()
    } else {
        list
    }
}