// WITH_STDLIB

fun test() {
    val x: List<Unit> = listOf(1, 2, 3).map {
        return@map <caret>Unit
    }
}