// WITH_STDLIB
fun test(charArray: CharArray?, len: Int): String {
    return buildString {
        <caret>append(charArray, 0, len)
    }
}