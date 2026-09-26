class Foo<T> {

    inner class Bar<U> {

        inner class Baz<V>(private val foo: T) {

            init {
                fo<caret>
            }
        }
    }
}

// EXIST: foo