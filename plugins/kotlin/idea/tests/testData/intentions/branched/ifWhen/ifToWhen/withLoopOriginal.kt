// WITH_STDLIB

fun testIf(xs: List<Any>) {
    for (x in xs) {
        <caret>if (x is String) continue
        else if (x is Int) break
        else println(x)
    }
}