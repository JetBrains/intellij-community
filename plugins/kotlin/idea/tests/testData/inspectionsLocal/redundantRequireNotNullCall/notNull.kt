// WITH_STDLIB
fun test(s: String) {
    println(1)
    <caret>requireNotNull(s)
    println(2)
}