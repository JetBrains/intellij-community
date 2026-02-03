// WITH_STDLIB
fun test(charArray: CharArray): String {
    return buildString {
        <caret>append(charArray, 2, 4)
    }
}
