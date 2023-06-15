// REPORT_NON_TRIVIAL_ACCESSORS: false
// PROBLEM: none
fun foo(j: J) {
    j.<caret>getX()
    j.setX(0)
}