// FIX: Replace with 'appendRange'
// WITH_STDLIB
fun test(charArray: CharArray, offset: Int, len: Int): String {
    return buildString {
        <caret>append(charArray, offset, len)
    }
}