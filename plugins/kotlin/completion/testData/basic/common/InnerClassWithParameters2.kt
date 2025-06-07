// IGNORE_K2

class Foo<T> {

    inner class Bar<U, V> {

        val bar: U

        init {
            ba<caret>
        }
    }
}

// EXIST: bar