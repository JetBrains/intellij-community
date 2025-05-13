// IGNORE_K2

class Foo<T> {

    inner class Bar<U, V>(private val foo: T) {

        init {
            fo<caret>
        }
    }
}

// EXIST: foo