class C {
    inner class Inner(s: String)
}

fun foo(c: C) {
    c.<caret>
}
