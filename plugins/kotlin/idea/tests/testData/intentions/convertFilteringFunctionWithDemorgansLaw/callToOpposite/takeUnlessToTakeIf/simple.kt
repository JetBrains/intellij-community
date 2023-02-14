// WITH_STDLIB
fun test(i: Int) {
    i.takeUnless<caret> { it != 1 }
}