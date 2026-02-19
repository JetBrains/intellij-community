// WITH_STDLIB

fun Sequence<Int>.test() {
    val x = <caret>map { "$it*$it" }.joinToString(
        prefix = "= ",
        separator = " + ",
    )
}
