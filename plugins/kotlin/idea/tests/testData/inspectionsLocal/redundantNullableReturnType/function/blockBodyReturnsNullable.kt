// PROBLEM: none
// WITH_STDLIB
fun foo(xs: List<Int>, b: Boolean): Int?<caret> {
    if (b) {
        return xs.first()
    } else {
        return xs.lastOrNull()
    }
}