// AFTER-WARNING: Parameter 'p' is never used
fun foo(a: Int) {
    <caret>when (a) {
        // some comment
        0 -> bar(a)

        // another comment
        1 -> bar(a)
    }
}

fun bar(p: Int){}

// IGNORE_FIR
