// IGNORE_K2

class Foo<E : Any> {
    fun foo(x: Any) {
        if (x is Bo<caret>) {

        }
    }

    inner class Boo {}
}

// ELEMENT: Boo