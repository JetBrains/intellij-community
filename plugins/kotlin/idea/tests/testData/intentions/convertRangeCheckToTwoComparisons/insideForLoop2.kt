// AFTER-WARNING: Name shadowed: bar
// AFTER-WARNING: Parameter 'bar' is never used
fun foo(bar: Int) {
    for (bar in 1..10) {
        if (bar in 1..10<caret>) {

        }
    }
}