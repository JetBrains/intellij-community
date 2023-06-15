// REPORT_NON_TRIVIAL_ACCESSORS: false
// PROBLEM: none
fun foo(j: J) {
    j.getX()
    j.<caret>setX(0)
}