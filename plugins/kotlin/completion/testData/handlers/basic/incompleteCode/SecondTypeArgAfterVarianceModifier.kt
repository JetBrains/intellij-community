class A<U, V>

fun foo() {
    A<Int, out Fil<caret>
}

// ELEMENT: File