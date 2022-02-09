// WITH_STDLIB
fun foo(a: Sequence<String>) {
    val b = a.<caret>asSequence<String>()
}