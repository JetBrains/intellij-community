// REPORT_NON_TRIVIAL_ACCESSORS: true
fun foo(j: J) {
    j.<caret>getX()
    j.setX(0)
}