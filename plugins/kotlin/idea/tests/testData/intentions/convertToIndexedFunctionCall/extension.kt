// INTENTION_TEXT: "Convert to 'forEachIndexed'"
// WITH_STDLIB
// AFTER-WARNING: Parameter 'index' is never used, could be renamed to _
fun List<String>.test() {
    <caret>forEach { s ->
        println(s)
    }
}