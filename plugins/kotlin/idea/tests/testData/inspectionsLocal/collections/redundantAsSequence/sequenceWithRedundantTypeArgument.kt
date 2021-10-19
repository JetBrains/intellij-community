// WITH_RUNTIME
fun foo(a: Sequence<String>) {
    val b = a.<caret>asSequence<String>()
}