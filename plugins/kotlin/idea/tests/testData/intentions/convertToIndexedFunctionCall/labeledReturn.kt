// INTENTION_TEXT: "Convert to 'forEachIndexed'"
// WITH_RUNTIME
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun test(list: List<String>) {
    list.<caret>forEach { s ->
        when (s) {
            "a" -> return@forEach
            "b" -> return@forEach
            else -> println(s)
        }
    }
}