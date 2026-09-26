class A<T>


fun String.test() {
    with(A()) {
        l<caret>
    }
}

// ORDER: length
