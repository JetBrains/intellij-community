// PROBLEM: none
// WITH_RUNTIME
fun foo(a: Sequence<String>): Sequence<CharSequence> {
    return a.<caret>asSequence<CharSequence>()
}