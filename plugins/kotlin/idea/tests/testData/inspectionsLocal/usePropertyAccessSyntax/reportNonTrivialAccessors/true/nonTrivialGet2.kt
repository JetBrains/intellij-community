// REPORT_NON_TRIVIAL_ACCESSORS: true
fun foo(k: K) {
    k.<caret>getX()
}

class K : J()