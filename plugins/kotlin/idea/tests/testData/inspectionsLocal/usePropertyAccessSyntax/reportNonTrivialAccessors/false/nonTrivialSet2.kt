// REPORT_NON_TRIVIAL_ACCESSORS: false
// PROBLEM: none
fun foo(k: K) {
    k.<caret>setX(0)
}

class K : J()