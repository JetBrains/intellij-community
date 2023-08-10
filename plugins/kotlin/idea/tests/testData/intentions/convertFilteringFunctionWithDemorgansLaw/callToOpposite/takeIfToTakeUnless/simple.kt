// WITH_STDLIB
fun test(i: Int) {
    i.takeIf<caret> { it != 1 }
}