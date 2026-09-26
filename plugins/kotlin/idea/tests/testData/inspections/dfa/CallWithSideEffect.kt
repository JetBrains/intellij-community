// WITH_STDLIB
fun writeString(s: String) {
    val len = s.length
    if (len == 0) {
        return
    }

    var charsWritten = 0
    while (charsWritten < len) {
        var i = 0
        while (i < 42) {
            val aChar = s.get(charsWritten++)
            println(aChar)
            i += 2
        }
    }
}