// FIR_IDENTICAL
// FIR_COMPARISON

enum class Foo

fun <T> T.genericExt() {}
fun test() {
    Foo.<caret>
}

// EXIST: valueOf
// ABSENT: genericExt
// ABSENT: toString