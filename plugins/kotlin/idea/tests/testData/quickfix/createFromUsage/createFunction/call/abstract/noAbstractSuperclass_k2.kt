// "Create abstract function 'Foo.bar'" "false"
// ACTION: Create function 'bar'
// ERROR: Unresolved reference: bar

open class A

class Foo : A() {
    fun foo() {
        <caret>bar()
    }
}

// IGNORE_K1