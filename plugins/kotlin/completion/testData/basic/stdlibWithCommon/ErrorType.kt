// FIR_IDENTICAL
fun <T> T.foo() {}
fun Any.foo() {}

fun Foo.test() {
    fo<caret>
}

// EXIST: for
// EXIST: floatArrayOf
// NOTHING_ELSE