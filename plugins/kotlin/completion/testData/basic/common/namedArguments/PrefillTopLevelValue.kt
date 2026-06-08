// FIR_IDENTICAL


val a = 5

fun foo(a: Int) {}

fun test() {
    foo(<caret>)
}

// EXIST: { itemText: "a = a" }