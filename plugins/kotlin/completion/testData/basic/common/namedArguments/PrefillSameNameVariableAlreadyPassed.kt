// FIR_IDENTICAL


fun foo(a: Int, b: Int) {}

fun test() {
    val a = 5
    val b = 10
    foo(a = 3, <caret>)
}

// ABSENT: { itemText: "a = a" }
// EXIST: { itemText: "b = b" }