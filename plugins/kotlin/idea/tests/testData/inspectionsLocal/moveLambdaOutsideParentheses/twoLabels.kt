// PROBLEM: none
fun test() {
    foo(bar@ foo@{ bar(it) }<caret>)
}

fun foo(f: (String) -> Int) {
    f("")
}

fun bar(s: String) = s.length