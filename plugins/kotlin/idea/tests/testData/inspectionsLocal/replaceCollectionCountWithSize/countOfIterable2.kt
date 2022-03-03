// PROBLEM: none
// WITH_STDLIB

fun foo(iterable: Iterable<String>?) {
    iterable?.<caret>count()
}
