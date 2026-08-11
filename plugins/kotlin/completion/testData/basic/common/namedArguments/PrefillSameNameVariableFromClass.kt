// FIR_IDENTICAL

class MyClass {
    val a: Int = 10
}

fun test(a: Int) {}

fun MyClass.usage() {
    test(<caret>)
}

// EXIST: { itemText: "a = a" }
// EXIST: { itemText: "a =", tailText: " Int" }
