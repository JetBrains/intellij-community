// IS_APPLICABLE: false
// WITH_STDLIB
fun nullableList(): List<Int>? = listOf()

fun test() {
    nullableList()?.forEach<caret> { println(it) }
}