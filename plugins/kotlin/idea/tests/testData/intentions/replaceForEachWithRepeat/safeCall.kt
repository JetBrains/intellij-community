// IS_APPLICABLE: false
// WITH_STDLIB
fun foo(range: IntRange?) {
    range?.<caret>forEach {
        println(it)
    }
}