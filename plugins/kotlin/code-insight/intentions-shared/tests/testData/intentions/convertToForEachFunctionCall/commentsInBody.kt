// WITH_STDLIB
// AFTER-WARNING: The value changed at 'v++' is never used
fun foo() {
    val list = 1..4

    <caret>for (x in list) { // start of loop
        // comment 1
        var v = x + 1
        // comment 2
        v++
        // end of loop
    }
}