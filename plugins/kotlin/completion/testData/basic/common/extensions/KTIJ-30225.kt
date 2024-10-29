// IGNORE_K2
class Foo(private val bar: () -> String) {

    fun foo() {
        bar().<caret>
    }
}

// EXIST: length