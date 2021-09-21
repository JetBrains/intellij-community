fun foo(b: Boolean) {
    <caret>if (b)
        bar(1) // comment1
    else bar(2) // comment2
// AFTER-WARNING: Parameter 'i' is never used
}

fun bar(i: Int) {}