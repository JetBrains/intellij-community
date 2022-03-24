// AFTER-WARNING: Parameter 'convert' is never used
// AFTER-WARNING: Parameter 'i' is never used
// AFTER-WARNING: Parameter 'j' is never used
// AFTER-WARNING: Parameter 'k' is never used
fun test() {
    foo2(<caret>::convert3)
}

fun foo2(convert: (Int, Int) -> Unit) {}

fun convert3(i: Int, j: Int, k: Int = 0) {}