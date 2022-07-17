// WITH_STDLIB
fun test(s: String) {
    println(1)
    kotlin.<caret>checkNotNull(s)
    println(2)
}