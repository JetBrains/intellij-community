// PROBLEM: none

interface MyInterface {
    class Nested
}

fun MyInterface.foo() {
    val p: MyInterface<caret>.Nested
}
