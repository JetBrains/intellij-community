// PROBLEM: none
class Foo {
    var instance: Foo get() = <caret>this
    set(value) {
        local = this
    }
}

var local: Foo? = null

