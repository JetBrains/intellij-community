// PROBLEM: none
// WITH_STDLIB

class My {
    operator fun invoke() {}
}

fun bar(my: List<My>) {
    my.for<caret>Each { it() }
}