// FIR_IDENTICAL


fun foo(a: Int) {}

fun test() {
    val a = 5
    foo(<caret>)
}

// EXIST: { itemText: "a = a" }
// EXIST: { itemText: "a =", tailText: " Int" }
