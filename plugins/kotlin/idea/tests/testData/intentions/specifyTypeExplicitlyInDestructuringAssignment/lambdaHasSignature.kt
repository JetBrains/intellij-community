// WITH_STDLIB
// AFTER-WARNING: Destructured parameter 'i' is never used
// AFTER-WARNING: Destructured parameter 's' is never used
fun test() {
    val list = emptyList<Pair<Int, String>>()
    list.forEach { (i, s)<caret>: Pair<Int, String> ->
    }
}