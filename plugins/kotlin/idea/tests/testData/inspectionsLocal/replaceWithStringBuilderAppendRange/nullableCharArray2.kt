// WITH_STDLIB
fun test(charArray: CharArray?, len: Int): String {
    return buildString {
        if (charArray != null) {
            <caret>append(charArray, 0, len)
        }
    }
}