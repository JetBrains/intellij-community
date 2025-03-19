// WITH_STDLIB

fun test(s: String?): Int? {
    return s?.let<caret> {
        it.length
    }
}