// WITH_RUNTIME

fun List<Int>.test() {
    val x = <caret>map { "$it*$it" }.joinToString(
        prefix = "= ",
        separator = " + ",
    )
}
