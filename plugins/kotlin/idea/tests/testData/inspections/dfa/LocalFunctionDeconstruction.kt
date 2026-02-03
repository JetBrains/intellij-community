// WITH_STDLIB

fun test(s: String): Int {
    var (l, r) = 0 to 0
    fun movePtr() {
        r++
    }
    var result = 0
    while (r < s.length) {
        result++
        movePtr()
    }
    return result
}