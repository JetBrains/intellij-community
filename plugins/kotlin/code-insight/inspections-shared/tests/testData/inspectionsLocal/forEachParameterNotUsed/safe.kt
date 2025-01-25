// WITH_STDLIB

fun test(list: List<String>?) {
    list?.for<caret>Each {}
}