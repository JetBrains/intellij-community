// FIR_IDENTICAL

fun foo(a: Int) {}

fun test() {
    val a = "hello"
    foo(<caret>)
}

// ABSENT: { itemText: "a = a" }
// EXIST: { itemText: "a =", tailText: " Int" }