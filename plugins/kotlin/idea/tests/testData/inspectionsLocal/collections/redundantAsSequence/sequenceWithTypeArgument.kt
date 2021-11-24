// PROBLEM: none
// WITH_STDLIB
fun foo(a: Sequence<String>): Sequence<CharSequence> {
    return a.<caret>asSequence<CharSequence>()
}