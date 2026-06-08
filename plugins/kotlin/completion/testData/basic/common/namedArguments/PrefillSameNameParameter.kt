// FIR_IDENTICAL


fun foo(a: Int) {}

fun test(a: Int) {
    foo(<caret>)
}

// EXIST: { itemText: "a = a" }