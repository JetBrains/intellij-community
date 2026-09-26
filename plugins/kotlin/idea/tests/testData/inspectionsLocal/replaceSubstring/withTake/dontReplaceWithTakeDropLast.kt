// PROBLEM: none
// WITH_STDLIB

fun foo(s: String): String? {
    val suf = ""
    if (s.endsWith(suffix = suf)) {
        return s.substring<caret>(0, s.length - suf.length)
    }
    return null
}