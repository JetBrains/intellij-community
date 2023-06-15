// REPORT_NON_TRIVIAL_ACCESSORS: true
fun foo(k: K) {
    k.<caret>getX()
    k.setX(0)
}

class K : J()