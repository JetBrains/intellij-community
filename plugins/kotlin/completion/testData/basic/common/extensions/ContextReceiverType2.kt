class A

class B {
    fun A.bar() {}
}

context(A, B)
fun test() {
    ba<caret>
}

// EXIST: bar
// IGNORE_K2