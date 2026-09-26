// WITH_STDLIB

fun test(b: Boolean) {
    val map = mutableMapOf<String, () -> Unit>()
    map.<caret>put("") {
        if (b) {
            return@put
        }
    }
}