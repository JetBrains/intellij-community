// WITH_STDLIB
fun test(charArray: CharArray, offset: Int, len: Int): String {
    return buildString {
        this.append<caret>(charArray, offset, len)
    }
}