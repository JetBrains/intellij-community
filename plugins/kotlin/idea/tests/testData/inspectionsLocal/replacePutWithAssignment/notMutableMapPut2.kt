// WITH_STDLIB
// PROBLEM: none
data class Bar(val map: MutableMap<String, Int>) : MutableMap<String, Int> by map {
    fun put(key: Int, value: String): String = value
}

fun test(bar: Bar) {
    bar.<caret>put(1, "1")
}
