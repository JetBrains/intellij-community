// WITH_STDLIB
fun test(s: String?) {
    requireNotNull(s)
    <caret>requireNotNull(s)
    println(1)
}