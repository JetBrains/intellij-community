// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(s: String) {
    s.<caret>forEach {
        println(it)
    }
}