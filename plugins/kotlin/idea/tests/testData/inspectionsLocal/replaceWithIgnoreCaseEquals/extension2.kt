// WITH_STDLIB
fun String.test(s: String): Boolean {
    return <caret>s.toLowerCase() == toLowerCase()
}
