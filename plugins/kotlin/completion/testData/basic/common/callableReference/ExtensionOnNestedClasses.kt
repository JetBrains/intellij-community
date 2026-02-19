class A {
    class B
}

fun A.B.foo() {}

fun main() {
    A.B::<caret>
}

// EXIST: { "itemText": "foo" }