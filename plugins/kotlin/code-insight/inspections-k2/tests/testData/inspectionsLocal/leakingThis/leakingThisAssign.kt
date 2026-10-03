// PROBLEM: Leaking this
// FIX: none
class Foo {
    init {
        global = <caret>this
    }
}

var global: Foo? = null