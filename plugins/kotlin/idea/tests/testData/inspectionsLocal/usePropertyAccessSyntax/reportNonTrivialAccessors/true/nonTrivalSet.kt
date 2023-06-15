// REPORT_NON_TRIVIAL_ACCESSORS: true
fun foo(j: J) {
    j.getX()
    j.<caret>setX(0)
}