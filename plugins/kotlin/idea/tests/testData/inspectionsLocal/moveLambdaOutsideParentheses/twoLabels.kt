// PROBLEM: none
// K2_ERROR: MULTIPLE_LABELS_ARE_FORBIDDEN
fun test() {
    foo(bar@ foo@{ bar(it) }<caret>)
}

fun foo(f: (String) -> Int) {
    f("")
}

fun bar(s: String) = s.length