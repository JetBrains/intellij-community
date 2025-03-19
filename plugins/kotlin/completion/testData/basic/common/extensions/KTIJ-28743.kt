// FIR_IDENTICAL
// FIR_COMPARISON
fun CharSequence.fooBar() {}

open class Foo(private val element: CharSequence)

class Bar(private val element: CharSequence) : Foo(element) {

    fun test() {
        element.<caret>
    }
}

// EXIST: fooBar