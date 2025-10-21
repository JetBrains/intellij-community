// PROBLEM: none
// IS_APPLICABLE: false
// WITH_STDLIB

val list = emptyList<caret><String>()
val emptyList: List<String> = emptyList<String>()

fun testMe(list: List<String>?) {
    val p = list ?: emptyList<String>()
    val q: List<String> = list ?: emptyList<String>()
}