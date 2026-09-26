// AFTER-WARNING: Parameter 'x' is never used
// AFTER-WARNING: Parameter 'y' is never used
// AFTER-WARNING: Parameter 'z' is never used
fun test(b: Boolean) {
    <caret>if (b) {
        println(1, 2, 3)
    } else {
        println(1, 4, 3)
    }
}

fun println(x: Int, y: Int, z: Int) {}