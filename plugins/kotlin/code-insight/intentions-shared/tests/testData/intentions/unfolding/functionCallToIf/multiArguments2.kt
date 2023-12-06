// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'z' is never used
fun test(b: Boolean) {
    <caret>println(
        1, if (b) {
            2
        } else {
            4
        }, 3
    )
}

fun println(x: Int, y: Int, z: Int) {}