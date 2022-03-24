// WITH_STDLIB
typealias ListInt = List<Int>

fun ListInt.boo() = firstOrNull()

fun main() {
    listOf(1, 2, 3).apply {<caret> this.boo() }
}
