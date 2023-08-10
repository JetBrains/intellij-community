// PROBLEM: none
// WITH_STDLIB
fun test(str: String): String?<caret> = str.run {
    return null
}