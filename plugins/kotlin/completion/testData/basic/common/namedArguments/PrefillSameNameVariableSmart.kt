// FIR_IDENTICAL

// COMPLETION_TYPE: SMART

fun foo(a: Int) {}

fun test() {
    val a = 5
    foo(<caret>)
}

// EXIST: { itemText: "a = a" }