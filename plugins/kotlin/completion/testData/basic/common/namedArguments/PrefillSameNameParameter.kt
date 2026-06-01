// FIR_IDENTICAL
// IGNORE_K1

fun foo(a: Int) {}

fun test(a: Int) {
    foo(<caret>)
}

// EXIST: { itemText: "a = a" }