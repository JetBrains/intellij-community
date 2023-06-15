// REPORT_NON_TRIVIAL_ACCESSORS: true
fun foo(k: K) {
    k.getX()
    k.<caret>setX(0)
}

class K : J()