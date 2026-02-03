// WITH_STDLIB
fun test(charArray: CharArray, a: Int, b: Int, c: Int, d: Int): String {
    return buildString {
        <caret>append(charArray, a - b, c - d)
    }
}