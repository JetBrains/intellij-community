// WITH_STDLIB
fun test(i: Int) {
    println(<caret>requireNotNull(i) { "" })
}