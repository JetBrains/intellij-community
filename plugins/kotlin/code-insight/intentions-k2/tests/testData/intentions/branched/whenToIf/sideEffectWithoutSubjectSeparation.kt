// The following example shows how it can have a side effect when we do not seperate subject from if condition
// PRIORITY: LOW

object G {
    var counter = 0
    fun getCounter() = counter++
}

fun test(x: Int, y: Int): String {
    when<caret> (G.getCounter()) {
        1 -> return "one"
        2 -> return "two"
        else -> return "big"
    }
}
