// WITH_STDLIB

fun test() {
    val map = mutableMapOf<String, () -> Unit>()
    map.<caret>put("") {
        listOf(1).forEach {
            return@forEach
        }
    }
}