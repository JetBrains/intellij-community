// FIR_IDENTICAL
// IGNORE_K1

val a = 5

fun foo(a: Int) {}

fun test() {
    foo(<caret>)
}

// EXIST: { itemText: "a = a" }