// WITH_STDLIB

fun test() {
    listOf(1,2,3).map { it::toS<caret>tring }
}