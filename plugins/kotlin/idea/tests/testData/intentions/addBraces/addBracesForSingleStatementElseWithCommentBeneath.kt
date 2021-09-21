fun test(b: Boolean) {
    if (b) foo(0)
    else<caret>
        while (true) {
            foo(1)
        }
    // comment about call below
// AFTER-WARNING: Parameter 'i' is never used
}

fun foo(i: Int) {}