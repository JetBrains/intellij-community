// PROBLEM: none
// K2_ERROR: Multiple labels per statement are forbidden.
fun test() {
    foo(bar@ foo@{ bar(it) }<caret>)
}

fun foo(f: (String) -> Int) {
    f("")
}

fun bar(s: String) = s.length