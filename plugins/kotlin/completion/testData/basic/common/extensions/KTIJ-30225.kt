// FIR_IDENTICAL
// FIR_COMPARISON
class Foo(private val bar: () -> String) {

    fun foo() {
        bar().<caret>
    }
}

// EXIST: length