// WITH_STDLIB

fun foo() {
    val x = listOf(1,2,3).map {<caret> Int::toString }
}