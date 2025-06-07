// FIR_IDENTICAL
fun <T> T.fooo() {}
fun Any.fooo() {}

fun Foo.test() {
    foo<caret>
}

fun fooo1() {}

// EXIST: fooo1
// NOTHING_ELSE